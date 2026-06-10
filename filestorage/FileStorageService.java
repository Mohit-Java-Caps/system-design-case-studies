package filestorage;

import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;

/**
 * FileStorageService — System design for a scalable file storage service (S3/Drive-style).
 *
 * SYSTEM DESIGN INTERVIEW: "Design a File Storage Service like Google Drive / S3"
 * ─────────────────────────────────────────────────────────────────────
 * Requirements:
 *   Functional:
 *     - Upload files of any size (up to 5 GB)
 *     - Download files with low latency
 *     - Share files (public URL / signed URL with TTL)
 *     - File deduplication (same file → same storage object)
 *     - Version history
 *
 *   Non-Functional:
 *     - 1 billion files stored, 100M active users
 *     - Upload: handle large files without memory issues
 *     - 99.999% durability (eleven 9s for S3)
 *     - Download < 100ms from CDN edge
 *
 * KEY DESIGN DECISIONS:
 * ─────────────────────────────────────────────────────────────────────
 *  1. CHUNKED UPLOAD:
 *     Large files (> 5MB) split into chunks.
 *     Each chunk uploaded independently and stored in object storage.
 *     If upload fails mid-way — resume from the failed chunk, not the start.
 *     S3 calls this: Multi-part Upload.
 *
 *  2. CONTENT-BASED DEDUPLICATION:
 *     Hash the file content (SHA-256).
 *     If hash already exists → point to same storage object (don't re-upload).
 *     Dropbox saves ~60% storage via deduplication across users.
 *
 *  3. METADATA vs CONTENT SEPARATION:
 *     Metadata (name, owner, size, hash) → PostgreSQL
 *     Content (bytes) → Object storage (S3, GCS)
 *     Never store file bytes in the DB.
 *
 *  4. CDN FOR DOWNLOADS:
 *     Files served via CDN edge nodes (CloudFront, Fastly).
 *     First request → cache miss → fetch from S3 → cached at edge.
 *     Subsequent requests → served from edge (< 10ms).
 *
 *  5. SIGNED URLS:
 *     For private files: generate signed URL with TTL.
 *     User downloads directly from CDN/S3 — no traffic through your server.
 *
 * Author: Mohit Kumar — github.com/Mohit-Java-Caps
 */
public class FileStorageService {

    // ── Models ────────────────────────────────────────────────────────────
    record FileMetadata(
        String fileId,
        String fileName,
        String ownerId,
        long sizeBytes,
        String contentHash,   // SHA-256 of file content
        String storageKey,    // path in object storage
        long uploadedAt,
        int version,
        boolean isPublic
    ) {}

    record Chunk(int index, byte[] data, String chunkHash) {}

    record UploadSession(String sessionId, String fileName, String ownerId,
                         long totalSize, int totalChunks, Map<Integer, String> uploadedChunks) {
        boolean isComplete() { return uploadedChunks.size() == totalChunks; }
        double progress()    { return (double) uploadedChunks.size() / totalChunks * 100; }
    }

    record SignedUrl(String url, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    // ── Object Storage mock (production: AWS S3, GCS, Azure Blob) ─────────
    static class ObjectStorage {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        void put(String key, byte[] data) {
            objects.put(key, data);
            System.out.printf("  [STORAGE] Stored object: %s (%d bytes)%n", key, data.length);
        }

        Optional<byte[]> get(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        boolean exists(String key) { return objects.containsKey(key); }
        long totalBytes()          { return objects.values().stream().mapToLong(b -> b.length).sum(); }
        int objectCount()          { return objects.size(); }
    }

    // ── Metadata DB (production: PostgreSQL) ──────────────────────────────
    static class MetadataStore {
        private final Map<String, FileMetadata>       byFileId   = new ConcurrentHashMap<>();
        private final Map<String, String>             byHash     = new ConcurrentHashMap<>(); // hash → storageKey
        private final Map<String, List<FileMetadata>> byOwner    = new ConcurrentHashMap<>();

        void save(FileMetadata meta) {
            byFileId.put(meta.fileId(), meta);
            byHash.put(meta.contentHash(), meta.storageKey());
            byOwner.computeIfAbsent(meta.ownerId(), k -> new CopyOnWriteArrayList<>()).add(meta);
        }

        Optional<FileMetadata> findById(String fileId) { return Optional.ofNullable(byFileId.get(fileId)); }
        Optional<String> findStorageKeyByHash(String hash) { return Optional.ofNullable(byHash.get(hash)); }
        List<FileMetadata> findByOwner(String ownerId) { return byOwner.getOrDefault(ownerId, List.of()); }
    }

    // ── The File Service ──────────────────────────────────────────────────
    static class FileService {
        private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB chunks
        private final ObjectStorage  storage;
        private final MetadataStore  metadata;
        private final Map<String, UploadSession> activeSessions = new ConcurrentHashMap<>();
        private long dedupSavedBytes = 0;

        FileService(ObjectStorage storage, MetadataStore metadata) {
            this.storage  = storage;
            this.metadata = metadata;
        }

        /** Step 1: Initiate a multi-part upload session */
        UploadSession initiateUpload(String fileName, String ownerId, long totalSizeBytes) {
            String sessionId  = UUID.randomUUID().toString();
            int totalChunks   = (int) Math.ceil((double) totalSizeBytes / CHUNK_SIZE);
            UploadSession session = new UploadSession(
                sessionId, fileName, ownerId, totalSizeBytes, totalChunks, new ConcurrentHashMap<>());
            activeSessions.put(sessionId, session);
            System.out.printf("[UPLOAD] Session %s | File: %s | Size: %s | Chunks: %d%n",
                sessionId.substring(0, 8), fileName, humanSize(totalSizeBytes), totalChunks);
            return session;
        }

        /** Step 2: Upload one chunk (can be parallelised) */
        void uploadChunk(String sessionId, Chunk chunk) {
            UploadSession session = activeSessions.get(sessionId);
            if (session == null) throw new IllegalArgumentException("Session not found");

            String chunkKey = "chunks/" + sessionId + "/chunk-" + chunk.index();
            storage.put(chunkKey, chunk.data());
            session.uploadedChunks().put(chunk.index(), chunkKey);
            System.out.printf("[UPLOAD] Chunk %d/%d uploaded (%.0f%% complete)%n",
                chunk.index() + 1, session.totalChunks(), session.progress());
        }

        /** Step 3: Complete upload — merge chunks, deduplicate, save metadata */
        FileMetadata completeUpload(String sessionId) {
            UploadSession session = activeSessions.remove(sessionId);
            if (!session.isComplete()) throw new IllegalStateException("Not all chunks uploaded");

            // Assemble file content from chunks (in order)
            byte[] fullContent = assembleChunks(session);
            String contentHash = sha256(fullContent);

            // DEDUPLICATION: check if this content already stored
            Optional<String> existingKey = metadata.findStorageKeyByHash(contentHash);
            String storageKey;

            if (existingKey.isPresent()) {
                storageKey = existingKey.get();
                dedupSavedBytes += fullContent.length;
                System.out.printf("[DEDUP] Content already exists — reusing storage object (saved %s)%n",
                    humanSize(fullContent.length));
            } else {
                storageKey = "files/" + contentHash.substring(0, 16) + "/" + session.fileName();
                storage.put(storageKey, fullContent);
            }

            // Save metadata
            String fileId = UUID.randomUUID().toString();
            FileMetadata meta = new FileMetadata(fileId, session.fileName(),
                session.ownerId(), fullContent.length, contentHash, storageKey,
                System.currentTimeMillis(), 1, false);
            metadata.save(meta);

            System.out.printf("[UPLOAD] Complete — fileId: %s | Hash: %s%n",
                fileId.substring(0, 8), contentHash.substring(0, 16));
            return meta;
        }

        /** Generate a signed URL for private file download */
        SignedUrl generateSignedUrl(String fileId, long ttlSeconds) {
            FileMetadata meta = metadata.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
            long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000;
            String signature = Integer.toHexString((fileId + expiresAt).hashCode());
            String url = "https://cdn.storage.io/" + meta.storageKey()
                + "?sig=" + signature + "&expires=" + expiresAt;
            return new SignedUrl(url, expiresAt);
        }

        private byte[] assembleChunks(UploadSession session) {
            // In production: do this as a server-side S3 CompleteMultipartUpload call
            List<byte[]> chunks = new ArrayList<>();
            for (int i = 0; i < session.totalChunks(); i++) {
                String chunkKey = session.uploadedChunks().get(i);
                chunks.add(storage.get(chunkKey).orElse(new byte[0]));
            }
            byte[] result = new byte[(int) session.totalSize()];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }

        private String sha256(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(data);
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        void printStats() {
            System.out.println("[STATS]");
            System.out.println("  Objects stored:     " + storage.objectCount());
            System.out.println("  Total bytes stored: " + humanSize(storage.totalBytes()));
            System.out.println("  Dedup savings:      " + humanSize(dedupSavedBytes));
        }

        static String humanSize(long bytes) {
            if (bytes < 1024)         return bytes + " B";
            if (bytes < 1024*1024)    return bytes/1024 + " KB";
            if (bytes < 1024*1024*1024) return bytes/1024/1024 + " MB";
            return bytes/1024/1024/1024 + " GB";
        }
    }

    // ── Main ──────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("=== File Storage Service Design Demo ===\n");

        ObjectStorage storage  = new ObjectStorage();
        MetadataStore metaDB   = new MetadataStore();
        FileService   service  = new FileService(storage, metaDB);

        // Scenario 1: Upload a file in 3 chunks
        System.out.println("━━━ Scenario 1: Multi-part Upload (15 MB file → 3 chunks) ━━━");
        byte[] fileContent = new byte[15 * 1024 * 1024]; // 15 MB
        new Random().nextBytes(fileContent);

        UploadSession session1 = service.initiateUpload("presentation.pdf", "user-01", fileContent.length);

        // Upload chunks (can be parallel in production)
        int chunkSize = 5 * 1024 * 1024;
        for (int i = 0; i < 3; i++) {
            int start = i * chunkSize;
            int end   = Math.min(start + chunkSize, fileContent.length);
            byte[] chunkData = Arrays.copyOfRange(fileContent, start, end);
            service.uploadChunk(session1.sessionId(),
                new Chunk(i, chunkData, "chunk-hash-" + i));
        }

        FileMetadata file1 = service.completeUpload(session1.sessionId());

        // Scenario 2: Same file uploaded again — deduplication kicks in
        System.out.println("\n━━━ Scenario 2: Deduplication (same file, different user) ━━━");
        UploadSession session2 = service.initiateUpload("presentation_copy.pdf", "user-02", fileContent.length);
        for (int i = 0; i < 3; i++) {
            int start = i * chunkSize;
            int end   = Math.min(start + chunkSize, fileContent.length);
            service.uploadChunk(session2.sessionId(),
                new Chunk(i, Arrays.copyOfRange(fileContent, start, end), "chunk-hash-" + i));
        }
        service.completeUpload(session2.sessionId());

        // Scenario 3: Generate signed URL
        System.out.println("\n━━━ Scenario 3: Signed URL for private file ━━━");
        SignedUrl url = service.generateSignedUrl(file1.fileId(), 3600); // 1 hour TTL
        System.out.println("  URL: " + url.url());
        System.out.println("  Expired: " + url.isExpired());

        // Scenario 4: List user files
        System.out.println("\n━━━ Scenario 4: List user-01's files ━━━");
        metaDB.findByOwner("user-01").forEach(f ->
            System.out.printf("  %s | %s | %s%n",
                f.fileId().substring(0, 8), f.fileName(),
                FileService.humanSize(f.sizeBytes())));

        System.out.println();
        service.printStats();

        System.out.println("""

            === Architecture Notes ===
            Chunk size: 5-100 MB (balance between parallelism and retry cost).
            Deduplication: SHA-256 hash → content-addressable storage.
            Metadata DB: PostgreSQL with file_id, owner_id, storage_key, content_hash.
            Object storage: S3 (11 nines durability via geo-redundant replication).
            CDN: CloudFront/Fastly in front of S3 — downloads bypass your servers.
            Resumable upload: client tracks last uploaded chunk index — resume on failure.
            Virus scanning: async job scans uploaded content via ClamAV before making public.
            """);
    }
}
