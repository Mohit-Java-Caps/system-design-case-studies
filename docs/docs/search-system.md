
# Search System – System Design Case Study (Google / E‑Commerce Search)

A **Search System** enables users to quickly find relevant information from large datasets.
It is a core component of many products:
- Google‑like search engines
- E‑commerce product search
- Document search
- Log search

This is a **high‑signal system design problem** because it tests understanding of:
- Indexing
- Query processing
- Relevance
- Scalability
- Freshness vs performance trade‑offs

---

## 1️⃣ Problem Statement

Design a system that:
- Indexes large amounts of data
- Supports keyword‑based search
- Returns relevant results quickly
- Scales with data size and traffic

Examples:
- Product search in an e‑commerce site
- Document search
- Basic web search (simplified)

---

## 2️⃣ Functional Requirements

✅ Index data for searching  
✅ Support keyword queries  
✅ Return ranked results  
✅ Handle frequent reads  

Optional (clarify in interview):
- Filters (price, category)?
- Sorting (relevance, date)?
- Autocomplete?
- Fuzzy matching?

---

## 3️⃣ Non‑Functional Requirements

✅ Low latency (search must be fast)  
✅ High availability  
✅ Scalability  
✅ Relevance over absolute accuracy  

Interview insight:
> **Users prefer fast and relevant results over perfectly accurate but slow results.**

---

## 4️⃣ Scale Assumptions (High Level)

Example:
- Millions of searchable items
- Thousands of searches per second
- Writes (index updates) << Reads (search queries)

This suggests:
✅ Read‑optimized design  
✅ Heavy caching  
✅ Distributed indexing  

---

## 5️⃣ Core Design Idea

> **Separate data storage from search indexing.**

Why?
- Databases are optimized for storage
- Search engines are optimized for querying text
- They scale differently

---

## 6️⃣ High‑Level Architecture

```

Client
|
v
API Gateway
|
+--> Search Service
\|        |
\|        +--> Search Index
|
+--> Data Service
|
+--> Primary Database

```

---

## 7️⃣ Core Components Explained

### 1️⃣ Data Service

- Manages core data (products, documents, etc.)
- Source of truth
- Handles CRUD operations

---

### 2️⃣ Search Index

Stores:
- Tokenized data
- Inverted indexes
- Metadata for ranking

Characteristics:
✅ Optimized for fast search  
✅ Read‑heavy  
✅ Eventually consistent  

Examples (conceptual):
- Elasticsearch‑style engines

---

### 3️⃣ Search Service

Responsible for:
✅ Parsing queries  
✅ Querying index  
✅ Ranking results  
✅ Returning response  

Stateless → easy to scale.

---

## 8️⃣ Indexing Strategy (Critical)

### Inverted Index (Core Concept)

Instead of:
```

Document → Words

```

Use:
```

Word → List of Documents

```

This allows:
✅ Fast keyword lookup  
✅ Efficient intersection for multi‑word queries  

---

## 9️⃣ Indexing Flow (Write Path)

1. Data is created/updated
2. Change event generated
3. Search index updated asynchronously

Asynchronous indexing:
✅ Avoids blocking writes  
✅ Improves system availability  

---

## 10️⃣ Search Query Flow (Read Path)

1. User submits search query
2. Search Service parses query
3. Query sent to index
4. Matching documents retrieved
5. Results ranked and returned

Read path is optimized heavily.

---

## 11️⃣ Ranking Strategy (Simplified)

Initial ranking:
✅ Keyword matching  
✅ Term frequency  

Advanced (optional):
- Popularity
- Recency
- User behavior

Interview tip:
> Start with basic relevance, evolve later.

---

## 12️⃣ Filtering & Sorting

Filters:
- Category
- Price
- Date

Filters are:
✅ Applied during search  
✅ Pre‑indexed for performance  

Sorting:
- Relevance (default)
- Time
- Popularity

---

## 13️⃣ Caching Strategy

Cache:
✅ Popular search queries  
✅ Trending searches  

Why?
- Search traffic is repetitive
- Heavy cache usage reduces index load

---

## 14️⃣ Handling Scale

✅ Shard index by document ID  
✅ Replicate shards for availability  
✅ Load balance search requests  

Reads scale horizontally.

---

## 15️⃣ Consistency Model

✅ Eventual consistency between DB and search index  

Trade‑off:
- Slightly stale search results
- Much higher availability and performance

Acceptable for most systems.

---

## 16️⃣ Failure Handling

### Search index down?
✅ Fallback message or limited functionality  

### Index lag?
✅ Old results temporarily visible  

System favors **availability over strict freshness**.

---

## 17️⃣ Trade‑Offs (Interview Gold)

### Why not search directly in DB?
❌ Slow  
❌ Not scalable  

### Why async indexing?
✅ Improved write performance  
❌ Slight delay in search visibility  

### Why inverted index?
✅ Fast keyword lookups  
❌ Index storage overhead  

---

## 18️⃣ Interview‑Ready Explanation (Use This)

> “Search systems separate primary data storage from search indexing. Data changes are indexed asynchronously using inverted indexes, allowing fast and scalable keyword search with relevance‑based ranking and eventual consistency.”

Clear. Structured. Senior‑level ✅

---

## 19️⃣ Key Takeaways

✅ Inverted index is fundamental  
✅ Reads dominate search systems  
✅ Async indexing improves reliability  
✅ Ranking evolves over time  

> **A good search system optimizes for speed, relevance, and scale—not perfect consistency.**

---

## What’s Next

You’ve now designed:
✅ URL Shortener  
✅ Rate Limiter  
✅ Notification System  
✅ File Storage System  
✅ News Feed System  
✅ Chat / Messaging System  
✅ Payment Processing System  
✅ Search System  

At this point, your system design coverage is **exceptional**.
