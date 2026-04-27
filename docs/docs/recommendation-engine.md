
# Recommendation Engine – System Design Case Study (Netflix / Amazon / YouTube)

A **Recommendation Engine** suggests relevant items to users based on their behavior
and preferences.

It is one of the **most advanced system design interview problems**, because it combines:
- Large‑scale data processing
- Machine learning concepts (high level)
- Real‑time serving systems
- Trade‑offs between relevance, latency, and cost

This case study focuses on **system design**, not ML math.

---

## 1️⃣ Problem Statement

Design a system that:
- Recommends items to users
- Personalizes results based on behavior
- Scales to millions of users and items
- Returns recommendations quickly

Examples:
- Amazon product recommendations
- Netflix movie suggestions
- YouTube video feed

---

## 2️⃣ Functional Requirements

✅ Show personalized recommendations  
✅ Update recommendations as user behavior changes  
✅ Support millions of users and items  

Optional (clarify in interview):
- Real‑time vs batch recommendations?
- Cold‑start users?
- Diversity vs accuracy?
- Multiple recommendation surfaces?

---

## 3️⃣ Non‑Functional Requirements

✅ Low latency (feed must load quickly)  
✅ Scalability  
✅ High availability  
✅ Eventual consistency acceptable  

Interview insight:
> **A slightly less accurate recommendation delivered fast is better than a perfect one delivered slowly.**

---

## 4️⃣ Scale Assumptions (High Level)

Example:
- Millions of users
- Millions of items
- Billions of interactions (views, clicks, likes)

Implication:
✅ Offline batch computation required  
✅ Online real‑time serving required  

---

## 5️⃣ Core Design Idea (Very Important)

> **Split recommendation system into two paths:**
> - Offline (heavy computation)
> - Online (fast serving)

This separation is fundamental.

---

## 6️⃣ High‑Level Architecture

```

User Activity
|
v
Event Stream
|
+--> Offline Processing (Batch)
\|         |
\|         +--> Recommendation Models
|
+--> Online Feature Store
|
v
Recommendation Service
|
v
Client

```

---

## 7️⃣ Offline Recommendation Pipeline (Batch Layer)

Purpose:
✅ Process large volumes of historical data  
✅ Generate recommendation candidates  

Typical inputs:
- User views
- Clicks
- Purchases
- Ratings

Processing happens:
✅ Periodically (hourly / daily)

---

### Offline Outputs

- User‑to‑item similarity
- Item‑to‑item similarity
- Precomputed recommendations

These results are:
✅ Stored in fast storage  
✅ Used by online serving layer  

---

## 8️⃣ Online Recommendation Serving

Purpose:
✅ Quickly return recommendations on request  

Responsibilities:
- Fetch precomputed candidates
- Apply lightweight ranking
- Apply filters (availability, rules)

Online layer must:
✅ Be very fast  
✅ Be stateless  
✅ Scale horizontally  

---

## 9️⃣ User Interaction Events

User actions:
- View
- Click
- Like
- Purchase

These events are:
✅ Published to event stream  
✅ Used for analytics  
✅ Used to retrain models  

Event‑driven design is critical.

---

## 10️⃣ Recommendation Strategies (High Level)

### 1️⃣ Content‑Based Filtering

- Recommend similar items to what user liked
- Uses item attributes

Pros:
✅ Works for new users  
✅ Explainable  

Cons:
❌ Limited diversity  

---

### 2️⃣ Collaborative Filtering

- Users similar to you liked these items

Pros:
✅ High personalization  

Cons:
❌ Cold‑start problem  

---

### 3️⃣ Hybrid Approach (Most Common)

> **Combine content‑based and collaborative filtering**

This balances:
✅ Accuracy  
✅ Coverage  
✅ Flexibility  

---

## 11️⃣ Cold‑Start Problem

For new users with no history:
✅ Popular items  
✅ Trending content  
✅ Category‑based recommendations  

Cold‑start is unavoidable and must be handled explicitly.

---

## 12️⃣ Data Storage Design

### User Events Store
Stores raw behavioral data.

### Recommendation Store
Stores:
- User → recommended item IDs
- Item → similar item IDs

Data is:
✅ Precomputed offline  
✅ Served online  

---

## 13️⃣ Caching Strategy

Cache:
✅ Top recommendations per user  
✅ Popular recommendation sets  

This reduces:
✅ Latency  
✅ Compute load  

---

## 14️⃣ Consistency Model

✅ Eventual consistency  

Reason:
- Model updates are periodic
- Immediate consistency is unnecessary

Users accept slight lag in personalization.

---

## 15️⃣ Failure Handling

### Offline pipeline failure
✅ Serve last known recommendations  

### Online service failure
✅ Fallback to popular content  

System must never:
❌ Return empty feed  

---

## 16️⃣ Scaling the System

✅ Stateless recommendation service  
✅ Horizontal scaling  
✅ Distributed storage  
✅ Separate offline and online workloads  

Each layer scales independently.

---

## 17️⃣ Trade‑Offs (Interview Gold)

### Why offline computation?
✅ Scales for large data  
❌ Less real‑time accuracy  

### Why online lightweight ranking?
✅ Low latency  
❌ Simplified logic  

### Why eventual consistency?
✅ High availability  
❌ Delayed personalization  

---

## 18️⃣ Interview‑Ready Explanation (Use This)

> “Recommendation systems separate offline batch processing from online serving. Offline pipelines analyze large volumes of user interaction data to generate recommendation candidates, while online services serve and rank these candidates quickly with low latency.”

Clear. Mature. Senior‑level ✅

---

## 19️⃣ Key Takeaways

✅ Recommendation engines are data‑heavy  
✅ Offline + online separation is essential  
✅ Event‑driven design feeds learning  
✅ Speed is as important as accuracy  

> **A great recommendation system balances relevance, latency, and scalability.**

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
✅ Recommendation Engine  
