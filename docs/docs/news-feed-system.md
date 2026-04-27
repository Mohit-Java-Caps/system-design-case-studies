
# News Feed System – System Design Case Study (Social Media Feed)

A **News Feed System** powers platforms like:
- Facebook
- Instagram
- LinkedIn
- Twitter/X (timeline)

This is a **core system design interview problem** because it brings together:
- High read/write traffic
- Fan‑out patterns
- Ranking and freshness
- Caching strategies
- Scalability trade‑offs

---

## 1️⃣ Problem Statement

Design a system that:
- Shows a personalized feed to users
- Displays posts from people they follow
- Supports high read traffic
- Updates feeds when new posts are created

---

## 2️⃣ Functional Requirements

✅ Users can create posts  
✅ Users can see a feed of posts  
✅ Feed shows posts from followed users  
✅ Posts ordered by time (initially)  

Optional (clarify in interview):
- Likes / comments?
- Ranking algorithms?
- Reposts / shares?
- Media (images/videos)?

---

## 3️⃣ Non‑Functional Requirements

✅ Low latency feed loading  
✅ High availability  
✅ Scalability (millions of users)  
✅ Eventual consistency acceptable  

Interview insight:
> **Users tolerate slightly stale feeds but not slow feeds.**

---

## 4️⃣ Scale Assumptions (High Level)

Example:
- Millions of users
- Thousands of follows per user (celebrities)
- Heavy read traffic compared to writes
- Spikes when popular users post

Understanding scale is critical for feed design.

---

## 5️⃣ Core Design Challenge

> **How do we efficiently deliver personalized feeds to millions of users?**

This boils down to a key decision:
➡️ **Fan‑out on write vs Fan‑out on read**

---

## 6️⃣ High‑Level Architecture

```

Client
|
v
API Gateway
|
+--> Feed Service
|
+--> Post Service
|
+--> Follow Service
|
+--> Database

```

---

## 7️⃣ Data Models (Simplified)

### User Follows

```

## FOLLOWS

user\_id
followed\_user\_id

```

---

### Posts

```

## POSTS

post\_id
user\_id
content
created\_at

```

---

### Feed (Materialized)

```

## USER\_FEED

user\_id
post\_id
created\_at

```

---

## 8️⃣ Strategy 1: Fan‑Out on Write (Push Model)

### How It Works

1. User creates a post
2. System fetches all followers
3. Post is pushed into each follower’s feed

```

Post → Followers → Feed Storage

```

---

### Advantages

✅ Fast feed reads  
✅ Precomputed feeds  
✅ Low read latency  

---

### Disadvantages

❌ Expensive for users with many followers  
❌ Heavy write amplification  

Example:
> A celebrity post creates millions of writes.

---

## 9️⃣ Strategy 2: Fan‑Out on Read (Pull Model)

### How It Works

1. User opens feed
2. System fetches recent posts from followed users
3. Merges and sorts posts at read time

```

Feed Request → Fetch Posts → Merge → Return

```

---

### Advantages

✅ No massive write bursts  
✅ Simple write path  

---

### Disadvantages

❌ Slow feed generation  
❌ Expensive reads  
❌ Complex ranking at runtime  

---

## ✅ Interview‑Recommended Hybrid Approach

> **Use fan‑out on write for normal users  
and fan‑out on read for high‑follower users.**

This hybrid approach balances:
✅ Read latency  
✅ Write scalability  

---

## 10️⃣ Feed Generation Flow (Hybrid)

### Post Creation

1. User creates post
2. System identifies follower count
3. If normal user → fan‑out on write
4. If celebrity → mark as pull‑based

---

### Feed Read

1. Fetch precomputed feed items
2. Fetch recent posts from celebrity users
3. Merge results
4. Sort and return feed

---

## 11️⃣ Caching Strategy

Caching is essential.

Cache:
✅ User feeds  
✅ Recent posts  
✅ Follow lists  

Do NOT cache:
❌ Full history indefinitely  

---

## 12️⃣ Feed Ranking (Simplified)

Initial implementation:
✅ Reverse chronological order  

Advanced (optional):
- Engagement score
- User affinity
- Content type weighting

Interview tip:
> Start simple. Ranking algorithms can evolve.

---

## 13️⃣ Handling New Posts

Use asynchronous processing:
- Post published event
- Feed update in background
- Non‑blocking for publisher

Message queues help absorb spikes.

---

## 14️⃣ Consistency Model

✅ Eventual consistency  

Why?
> Users prefer availability and speed over exact ordering.

---

## 15️⃣ Failure Handling

✅ Feed cache miss → fallback to DB  
✅ Feed rebuild possible  
✅ Post service independent  

System degrades gracefully.

---

## 16️⃣ Scaling the System

✅ Stateless services  
✅ Horizontal scaling  
✅ Sharded databases  
✅ Distributed caches  

Feed reads scale independently from writes.

---

## 17️⃣ Trade‑Offs (Interview Gold)

### Why hybrid model?
✅ Best balance of read/write cost  
❌ Increased complexity  

### Why eventual consistency?
✅ High availability  
❌ Slight feed staleness  

---

## 18️⃣ Interview‑Ready Explanation (Use This)

> “News Feed systems use either fan‑out on write or fan‑out on read. A hybrid approach is commonly used: pushing posts for normal users and pulling posts from high‑follower users at read time to balance scalability and latency.”

Clear. Mature. Senior‑level ✅

---

## 19️⃣ Key Takeaways

✅ Feed systems are read‑heavy  
✅ Fan‑out strategy is the core decision  
✅ Hybrid model scales best  
✅ Caching is mandatory  

> **A good feed system optimizes for fast reads, not perfect consistency.**

---

## What’s Next

You’ve now designed:
✅ URL Shortener  
✅ Rate Limiter  
✅ Notification System  
✅ File Storage System  
✅ News Feed System  
