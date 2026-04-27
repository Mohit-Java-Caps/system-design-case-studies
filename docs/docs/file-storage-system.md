
# File Storage System – System Design Case Study (Dropbox / Google Drive)

A **File Storage System** is a classic large‑scale system design problem.
It tests an engineer’s understanding of:
- Large file handling
- Metadata vs data separation
- Scalability and durability
- Latency vs cost trade‑offs
- Consistency and fault tolerance

This case study designs a **reliable, scalable, cloud‑friendly file storage system**.

---

## 1️⃣ Problem Statement

Design a system that:
- Allows users to upload files
- Allows users to download files
- Stores files reliably
- Scales to millions of users

Examples:
- Cloud drive
- Photo storage
- Document storage

---

## 2️⃣ Functional Requirements

✅ Upload files  
✅ Download files  
✅ Support large files  
✅ File metadata management  

Optional (clarify in interview):
- Sharing / permissions?
- Versioning?
- Folder structure?
- Deduplication?

---

## 3️⃣ Non‑Functional Requirements

✅ High durability (no data loss)  
✅ High availability  
✅ Scalability  
✅ Low latency for downloads  
✅ Cost efficiency  

Interview insight:
> **File storage systems prioritize durability over strong consistency.**

---

## 4️⃣ Scale Assumptions (High Level)

Example:
- Millions of users
- Billions of files
- Files ranging from KBs to GBs
- Reads >> writes

These assumptions guide:
✅ Storage choice  
✅ Caching strategy  
✅ Architecture split  

---

## 5️⃣ Key Design Idea (Very Important)

> **Separate file metadata from file content.**

Why?
- Metadata is small and frequently accessed
- File content is large and expensive to move
- Different scaling characteristics

This separation is critical.

---

## 6️⃣ High‑Level Architecture

```

Client
|
v
API Gateway
|
+--> Metadata Service -----> Metadata DB
|
+--> File Upload Service --> Object Storage
|
+--> File Download Service -> Object Storage

```

---

## 7️⃣ Core Components Explained

### 1️⃣ API Gateway

- Authentication
- Authorization
- Request routing
- Rate limiting

Keeps backend services protected.

---

### 2️⃣ Metadata Service

Stores:
- File ID
- File name
- Size
- Owner
- Location in storage
- Permissions

Metadata is:
✅ Small  
✅ Frequently accessed  

---

### 3️⃣ Metadata Database

Requirements:
✅ Fast reads  
✅ Indexing  
✅ Strong consistency  

Usually:
- Relational DB
- or Key‑Value store

---

### 4️⃣ Object Storage (File Content)

Used for:
✅ Actual file bytes  

Characteristics:
- Extremely durable
- Scales automatically
- Optimized for large blobs

Examples (conceptual):
- S3‑style object storage

---

## 8️⃣ File Upload Flow (Step‑by‑Step)

1. Client requests file upload
2. Metadata service creates file record
3. Upload service gives pre‑signed upload URL
4. Client uploads file directly to storage
5. Metadata updated with final status

Key insight:
✅ **Clients upload directly to storage**, not through backend servers

This avoids:
❌ Backend bottlenecks  
❌ Unnecessary data transfer  

---

## 9️⃣ Handling Large Files (Chunking)

Large files are uploaded in **chunks**.

Flow:
- Split file into chunks
- Upload chunks independently
- Combine logically via metadata

Advantages:
✅ Resume uploads  
✅ Parallelism  
✅ Fault tolerance  

---

## 10️⃣ File Download Flow

1. Client requests file
2. Metadata validated (permissions)
3. Download service returns pre‑signed URL
4. Client downloads directly from storage

This ensures:
✅ Low latency  
✅ High throughput  

---

## 11️⃣ Caching Strategy

Cache:
- Metadata
- Folder structures
- File lists

Do NOT cache:
❌ File content (too large)

---

## 12️⃣ Data Durability & Replication

Object storage provides:
✅ Multi‑replica storage  
✅ Cross‑zone replication  
✅ Automatic recovery  

No need to reinvent durability logic.

---

## 13️⃣ Consistency Model

Metadata:
✅ Strongly consistent  

File content:
✅ Eventually consistent  

Reason:
> Users care more about data safety than immediate visibility.

---

## 14️⃣ Security Considerations

✅ Authentication & authorization  
✅ Access control on objects  
✅ Encrypted storage  
✅ HTTPS for all transfers  

Never expose raw storage paths.

---

## 15️⃣ Failure Handling

### Upload Failure
✅ Retry chunk upload  

### Storage Node Failure
✅ Automatic replication  

### Metadata DB Failure
✅ Failover / replicas  

Design assumes:
> **Failures will happen.**

---

## 16️⃣ Scaling the System

✅ Stateless services  
✅ Horizontally scalable  
✅ Object storage auto‑scales  
✅ Metadata DB sharding (future)

---

## 17️⃣ Trade‑Offs (Interview Gold)

### Why separate metadata & content?
✅ Independent scaling  
✅ Performance  
❌ Slightly more complexity  

### Why object storage?
✅ Durability & scale  
❌ Higher latency than local disk  

---

## 18️⃣ Interview‑Ready Explanation (Use This)

> “File storage systems separate metadata from file content. Metadata is stored in a database, while file data is stored in durable object storage. Clients upload and download directly using pre‑signed URLs, allowing the system to scale while maintaining high durability.”

Clear. Calm. Senior‑level ✅

---

## 19️⃣ Key Takeaways

✅ Separate metadata from data  
✅ Use object storage for durability  
✅ Direct client uploads for scale  
✅ Chunking enables large file support  

> **File storage systems succeed by moving bytes efficiently and metadata carefully.**

---

## What’s Next

You’ve now designed:
✅ URL Shortener  
✅ Rate Limiter  
✅ Notification System  
✅ File Storage System  
