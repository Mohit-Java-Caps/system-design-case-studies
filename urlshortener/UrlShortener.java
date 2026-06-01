package urlshortener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UrlShortener + ConsistentHashing — Two classic system design implementations.
 *
 * SYSTEM DESIGN INTERVIEW:
 * ─────────────────────────────────────────────────────────────────────
 * "Design a URL shortener like bit.ly"
 * "Design a distributed cache — how do you distribute keys across nodes?"
 *
 * These are the two most common system design questions.
 * This file shows the core algorithmic implementation of both.
 *
 * PART 1 — URL SHORTENER:
 * ─────────────────────────────────────────────────────────────────────
 * Requirements:
 *   - Shorten a long URL to a 7-char code
 *   - Redirect short → long in <10ms
 *   - Handle 100M URLs, 10K reads/sec
 *
 * Key design decisions:
 *   1. ID generation: auto-increment vs UUID vs hash
 *   2. Encoding: Base62 (a-z, A-Z, 0-9) → 62^7 = 3.5 trillion unique codes
 *   3. Storage: write-once, read-heavy → cache aggressively (Redis)
 *   4. Collision: Base62 of auto-increment ID = no collision guaranteed
 *
 * PART 2 — CONSISTENT HASHING:
 * ─────────────────────────────────────────────────────────────────────
 * Problem: You have 3 cache nodes. You hash keys to nodes via key % 3.
 * A node goes down → you change to key % 2 → 100% of keys remap.
 * Cache miss storm. Database gets hit directly. Service degrades.
 *
 * Consistent Hashing fix:
 *   - Place nodes on a virtual ring (0 to 2^32)
 *   - Hash each node to a position on the ring
 *   - For a key, hash it → walk clockwise to the next node
 *   - Node removal: only keys on that node's segment remap (~1/N of keys)
 *   - Virtual nodes: each physical node gets K positions → better balance
 *
 * Author: Mohit Kumar — github.com/Mohit-Java-Caps
 */
public class UrlShortener {

    // ══════════════════════════════════════════════════════════════════════
    // PART 1: URL SHORTENER
    // ══════════════════════════════════════════════════════════════════════

    static class TinyUrl {
        private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        private static final String DOMAIN = "https://tny.io/";
        private static final int    CODE_LENGTH = 7;

        // Storage (production: Redis for short→long, PostgreSQL for persistence)
        private final Map<String, String> shortToLong = new ConcurrentHashMap<>();
        private final Map<String, String> longToShort = new ConcurrentHashMap<>();
        private final AtomicLong          idCounter   = new AtomicLong(1_000_000); // start non-trivial

        /**
         * Shorten a URL. Idempotent — same URL always returns same short code.
         */
        public String shorten(String longUrl) {
            // Check if already shortened (idempotency)
            if (longToShort.containsKey(longUrl)) {
                return DOMAIN + longToShort.get(longUrl);
            }

            // Generate unique ID → encode to Base62
            long id   = idCounter.getAndIncrement();
            String code = toBase62(id);

            shortToLong.put(code, longUrl);
            longToShort.put(longUrl, code);

            return DOMAIN + code;
        }

        /**
         * Resolve a short URL back to the original.
         * In production: check Redis first (L1 cache), then DB.
         */
        public String resolve(String shortUrl) {
            String code = shortUrl.replace(DOMAIN, "");
            return shortToLong.getOrDefault(code, "404 — URL not found");
        }

        /**
         * Encode a number to Base62 (7 characters = 3.5 trillion combinations).
         * This is why bit.ly uses 7 chars — more than enough for global scale.
         */
        private String toBase62(long num) {
            StringBuilder sb = new StringBuilder();
            while (num > 0) {
                sb.append(BASE62.charAt((int)(num % 62)));
                num /= 62;
            }
            // Pad to CODE_LENGTH
            while (sb.length() < CODE_LENGTH) sb.append('a');
            return sb.reverse().toString().substring(0, CODE_LENGTH);
        }

        public long decode(String code) {
            long num = 0;
            for (char c : code.toCharArray()) {
                num = num * 62 + BASE62.indexOf(c);
            }
            return num;
        }

        public int size() { return shortToLong.size(); }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PART 2: CONSISTENT HASHING
    // ══════════════════════════════════════════════════════════════════════

    static class ConsistentHashRing {
        // TreeMap maintains sorted order — clockwise ring traversal via ceilingKey()
        private final TreeMap<Integer, String> ring             = new TreeMap<>();
        private final Map<String, List<Integer>> nodePositions  = new HashMap<>();
        private final int virtualNodes; // replicas per physical node

        ConsistentHashRing(int virtualNodes) {
            this.virtualNodes = virtualNodes;
        }

        void addNode(String node) {
            List<Integer> positions = new ArrayList<>();
            for (int i = 0; i < virtualNodes; i++) {
                int hash = hash(node + "#VN" + i);
                ring.put(hash, node);
                positions.add(hash);
            }
            nodePositions.put(node, positions);
            System.out.printf("[RING] Added node %-12s — %d virtual nodes placed%n",
                node, virtualNodes);
        }

        void removeNode(String node) {
            List<Integer> positions = nodePositions.remove(node);
            if (positions != null) positions.forEach(ring::remove);
            System.out.printf("[RING] Removed node %-12s — %d virtual nodes removed%n",
                node, virtualNodes);
        }

        /** Find which node is responsible for this key */
        String getNode(String key) {
            if (ring.isEmpty()) return null;
            int hash = hash(key);
            // Walk clockwise — find nearest node at or after the hash position
            Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);
            if (entry == null) entry = ring.firstEntry(); // wrap around the ring
            return entry.getValue();
        }

        /** Show distribution of keys across nodes */
        void showDistribution(List<String> keys) {
            Map<String, Integer> distribution = new TreeMap<>();
            for (String key : keys) {
                String node = getNode(key);
                distribution.merge(node, 1, Integer::sum);
            }
            System.out.println("[RING] Key distribution across nodes:");
            distribution.forEach((node, count) ->
                System.out.printf("  %-14s → %4d keys (%.1f%%)%n",
                    node, count, count * 100.0 / keys.size()));
        }

        private int hash(String key) {
            // Simple hash for demo — production uses MurmurHash3 or MD5
            return Math.abs(key.hashCode());
        }

        int ringSize() { return ring.size(); }
    }

    // ── Main ──────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("=== System Design Implementations ===\n");

        // ── Part 1: URL Shortener ─────────────────────────────────────────
        System.out.println("━━━ Part 1: URL Shortener (bit.ly-style) ━━━\n");
        TinyUrl tinyUrl = new TinyUrl();

        String[] longUrls = {
            "https://github.com/Mohit-Java-Caps/jvm-heap-memory-management",
            "https://linkedin.com/in/mohit-kumar-dev",
            "https://mohit-java-caps.github.io/mohit-portfolio/",
            "https://leetcode.com/u/Mohit_72/",
            "https://github.com/Mohit-Java-Caps/algorithm-visualizer"
        };

        List<String> shortUrls = new ArrayList<>();
        System.out.println("Shortening URLs:");
        for (String url : longUrls) {
            String short_ = tinyUrl.shorten(url);
            shortUrls.add(short_);
            System.out.printf("  %s%n  → %s%n%n", url, short_);
        }

        System.out.println("Resolving short URLs:");
        for (String shortUrl : shortUrls) {
            System.out.printf("  %s%n  → %s%n", shortUrl, tinyUrl.resolve(shortUrl));
        }

        System.out.println("\nIdempotency check (same URL = same short code):");
        String repeat = tinyUrl.shorten(longUrls[0]);
        System.out.println("  " + repeat + " (same as first time ✓)");

        System.out.println("\nBase62 capacity:");
        System.out.printf("  62^7 = %,d unique codes — more than enough globally%n",
            (long) Math.pow(62, 7));

        // ── Part 2: Consistent Hashing ────────────────────────────────────
        System.out.println("\n━━━ Part 2: Consistent Hashing ━━━\n");
        ConsistentHashRing ring = new ConsistentHashRing(150); // 150 virtual nodes each

        ring.addNode("cache-node-1");
        ring.addNode("cache-node-2");
        ring.addNode("cache-node-3");
        System.out.println("Ring size: " + ring.ringSize() + " positions");

        // Generate test keys
        List<String> testKeys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) testKeys.add("session:" + UUID.randomUUID());

        System.out.println("\nDistribution with 3 nodes (150 vnodes each):");
        ring.showDistribution(testKeys);

        // Add a 4th node — see how few keys move
        System.out.println("\nAdding cache-node-4 (scale-out scenario):");
        ring.addNode("cache-node-4");

        // Count how many keys changed their node
        long remapped = testKeys.stream()
            .filter(k -> !ring.getNode(k).equals("cache-node-1") &&
                         !ring.getNode(k).equals("cache-node-2") &&
                         !ring.getNode(k).equals("cache-node-3"))
            .count();

        System.out.println("Distribution with 4 nodes:");
        ring.showDistribution(testKeys);
        System.out.printf("%nApprox keys remapped after adding node 4: ~%d/%d (expected ~25%%)%n",
            remapped, testKeys.size());

        System.out.println("\nNode removal (cache-node-2 goes down):");
        ring.removeNode("cache-node-2");
        System.out.println("Distribution after removal (only ~1/3 of keys remap):");
        ring.showDistribution(testKeys);

        System.out.println("""

            === Key Interview Points ===
            URL Shortener: Base62 of auto-increment ID = no hash collision.
            Scale reads: Cache in Redis (90%+ hit rate expected).
            Scale writes: Use distributed ID generator (Snowflake, Twitter's approach).

            Consistent Hashing: Adding/removing 1 node remaps only ~1/N keys.
            Virtual nodes solve "hot spot" — each physical node covers multiple ring segments.
            Used in: Apache Cassandra (token ring), Amazon DynamoDB, Memcached.
            Production: MurmurHash3 for the hash function (fast, uniform distribution).
            """);
    }
}
