
# Rate Limiter – System Design Case Study

A **Rate Limiter** controls how many requests a user or service can make within a given time window.

This is a **high‑signal system design question** because it tests:
- Traffic control
- Distributed consistency
- Performance vs accuracy trade‑offs
- Real production scenarios

---

## 1️⃣ Problem Statement

Design a system that:
- Limits the number of requests
- Per user / per IP / per API key
- Within a configurable time window

Example:
```

Allow 100 requests per minute per user

```

---

## 2️⃣ Why Rate Limiting Is Needed

Without rate limiting:
❌ System abuse (bots, scrapers)  
❌ Denial‑of‑Service attacks  
❌ Resource exhaustion  
❌ Unfair resource usage  

Rate limiting protects:
✅ APIs  
✅ Databases  
✅ Infrastructure cost  

---

## 3️⃣ Functional Requirements

✅ Limit requests per user / IP  
✅ Allow configurable rules  
✅ Fast decision per request  

Optional (clarify with interviewer):
- Burst handling?
- Per‑endpoint limits?
- Distributed deployment?

---

## 4️⃣ Non‑Functional Requirements

✅ Low latency (decision must be fast)  
✅ High availability  
✅ Scalability  
✅ Accuracy (reasonable, not perfect)  

Interview insight:
> **Rate limiting favors availability over perfect consistency.**

---

## 5️⃣ High‑Level Architecture

```

Client
|
v
API Gateway / Rate Limiter Service
|
v
Fast Storage (Redis / In‑memory)

```

Rate limiting is usually enforced:
- At **API Gateway**
- Or as a **middleware**

---

## 6️⃣ Where to Apply Rate Limiting

✅ API Gateway (most common)  
✅ Load balancer level  
✅ Service level (internal APIs)  

Best practice:
> Apply rate limiting as close to the edge as possible.

---

## 7️⃣ Core Algorithms (Interview Gold)

There are **four common rate‑limiting algorithms**.

---

### ✅ 1. Fixed Window Counter

**How it works**
- Count requests in a fixed window (e.g., 1 minute)
- Reset counter after window ends

Example:
```

Window: 10:00–10:01
Max: 100 requests

```

✅ Simple  
❌ Burst issue at window boundary  

---

### ✅ 2. Sliding Window (Improved Fixed Window)

Instead of fixed reset:
- Calculate requests in rolling window

✅ Smoother control  
❌ Slightly more computation  

---

### ✅ 3. Token Bucket (Most Common)

**Concept**
- Tokens added at constant rate
- Each request consumes a token
- If no token → reject request

✅ Allows bursts  
✅ Very flexible  
✅ Widely used in production  

---

### ✅ 4. Leaky Bucket

**Concept**
- Requests are queued
- Processed at fixed rate

✅ Smooth traffic  
❌ Bursts rejected  

---

## ✅ Interview‑Recommended Choice

> **Token Bucket** is the best general‑purpose algorithm

Because:
✅ Supports bursts  
✅ Easy to reason about  
✅ Real‑world friendly  

---

## 8️⃣ Data Storage Choice

### In‑Memory (Single Instance)

✅ Very fast  
❌ Not scalable  

Used only for:
- Single server apps
- Prototypes

---

### Distributed Storage (Recommended)

✅ Redis (or similar)

Why Redis?
- Fast atomic operations
- TTL support
- Shared across instances

---

## 9️⃣ Redis Data Model Example

Key:
```

rate\_limit:{userId}

```

Value:
- Remaining tokens
- Last refill timestamp

TTL:
✅ Automatically evicts inactive users

---

## 10️⃣ Request Flow (Critical)

1. Request arrives
2. Rate limiter checks token count
3. If token available:
   ✅ Allow request
4. Else:
   ❌ Reject with 429 (Too Many Requests)

---

## 11️⃣ Handling Distributed Systems

Key challenge:
❌ Multiple service instances

Solution:
✅ Centralized fast storage (Redis)
✅ Atomic updates (`INCR`, Lua scripts)

Interview insight:
> “Perfect consistency is less important than availability.”

---

## 12️⃣ Failure Handling

If Redis is down:
✅ Fail open (allow requests) OR  
✅ Fail closed (block requests)

Trade‑off:
- Security vs availability

Mention this trade‑off explicitly — interviewers love it.

---

## 13️⃣ Rate Limiting Responses

On limit exceeded:
```

HTTP 429 – Too Many Requests

```

Headers:
```

Retry‑After: 60

```

---

## 14️⃣ Scaling the System

✅ Stateless rate limiter instances  
✅ Horizontal scaling  
✅ Sharded Redis if needed  

---

## 15️⃣ Common Interview Mistakes

❌ Over‑engineering  
❌ Ignoring burst traffic  
❌ Forgetting distributed consistency  
❌ Skipping failure modes  

---

## 16️⃣ Interview‑Ready Explanation (Use This)

> “Rate limiter controls request traffic using algorithms like token bucket. It is usually implemented at API Gateway using Redis for distributed consistency, favoring availability and performance over perfect accuracy.”

✅ Clear  
✅ Structured  
✅ Senior‑level  

---

## 17️⃣ Key Takeaways

✅ Rate limiting protects systems  
✅ Token bucket is most practical  
✅ Redis enables distributed control  
✅ Trade‑offs must be discussed  

> **A good rate limiter fails gracefully, not perfectly.**

---

## What’s Next

With rate limiting done, the next system adds:
- Multiple components
- Asynchronous behavior
- Fan‑out operations

➡️ **Notification System Design**
