
# URL Shortener System Design (TinyURL / Bitly Style)

Designing a URL shortener is one of the **most common system design interview problems**.
It looks simple, but it tests many important concepts:
- Scalability
- Data modeling
- Read vs write optimization
- Caching
- Availability
- Trade‑offs

This case study walks through the design **step by step**, exactly how you should approach it in interviews.

---

## 1️⃣ Problem Statement

Design a system that:
- Takes a long URL as input
- Generates a short URL
- Redirects users from the short URL to the original URL

Example:
```

<https://www.example.com/some/very/long/url>
→ <https://tiny.ly/abc123>

```

---

## 2️⃣ Functional Requirements

✅ Generate a short URL for a given long URL  
✅ Redirect short URL to the original URL  
✅ Handle millions of redirects per day  

### Optional (Clarify with interviewer)
- Custom aliases?
- Expiry time?
- Analytics (click count)?

---

## 3️⃣ Non‑Functional Requirements

✅ Low latency (redirects must be fast)  
✅ High availability (system should always redirect)  
✅ Scalability (handle traffic growth)  
✅ Durability (URLs should not disappear)  

Interview tip:
> For URL shorteners, **read traffic is much higher than write traffic**.

---

## 4️⃣ Scale Estimation (Very High Level)

Assumptions (state clearly in interview):

- 100 million URLs created per day
- 10:1 read‑to‑write ratio
- Peak read traffic: ~10,000 requests/sec

We don’t need exact math — just **demonstrate awareness of scale**.

---

## 5️⃣ High‑Level Architecture

```

Client
|
v
API Gateway
|
v
URL Shortener Service
|
+--> Cache (Redis)
|
+--> Database

```

Explanation:
- Client hits API Gateway
- Requests routed to URL service
- Cache used for fast redirect
- Database is source of truth

---

## 6️⃣ API Design (Important)

### Create Short URL
```

POST /shorten
Body: { longUrl }
Response: { shortUrl }

```

### Redirect
```

GET /{shortCode}
→ HTTP 302 Redirect

```

Use **302 redirect** to avoid caching incorrect redirects at browser level.

---

## 7️⃣ Database Design

### Table Structure

```

## URL\_MAPPING

id (primary key)
short\_code (unique)
long\_url
created\_at
expiry\_date (optional)

```

Key points:
✅ `short_code` must be indexed  
✅ Lookups must be extremely fast  

---

## 8️⃣ Short Code Generation (Core Design Decision)

### Option 1: Random String

Generate:
```

\[a-zA-Z0-9] → length 6–8

```

Pros:
✅ Simple  
✅ No single point of failure  

Cons:
❌ Collision handling required  

---

### Option 2: Counter + Base62 (Common Choice)

1. Generate unique numeric ID
2. Convert to Base62

Example:
```

ID = 125 → Base62 = "cb"

```

Pros:
✅ No collision  
✅ Compact  

Cons:
❌ Requires centralized ID generation  

---

### Interview‑Ready Choice

> **Use counter + Base62 for simplicity and predictability.**

---

## 9️⃣ Redirect Flow (Read Path – Most Important)

Redirect requests dominate traffic.

Flow:

1. User hits `tiny.ly/abc123`
2. Service checks cache
3. If cache hit → redirect immediately
4. If cache miss → DB lookup → update cache → redirect

✅ Cache protects database  
✅ Low latency  

---

## 10️⃣ Caching Strategy (Critical)

Cache key:
```

short\_code → long\_url

```

Why cache?
- Redirects are read‑heavy
- Popular URLs accessed repeatedly

Use:
✅ Redis / in‑memory cache  
✅ TTL based on expiry  

Interview line:
> “Cache is essential because redirect traffic is hot‑key heavy.”

---

## 11️⃣ Handling Cache Miss

On cache miss:
- Query database
- Populate cache
- Return redirect

Cache warmup happens naturally over time.

---

## 12️⃣ Data Consistency

Source of truth:
✅ Database

Cache:
❌ Not source of truth  
✅ Can be rebuilt  

If cache fails:
✅ System still works via DB  

---

## 13️⃣ Handling Expired URLs

On redirect:
- Check expiry date
- If expired → return 404 or custom message

Expiry cleanup can be:
- Lazy (on access)
- Background job

---

## 14️⃣ Scaling the System

### Horizontal Scaling

- Stateless URL service
- Multiple instances behind load balancer

### Database Scaling

- Read replicas
- Sharding by short_code (future scale)

### Cache Scaling

- Distributed cache cluster

---

## 15️⃣ Fault Tolerance

✅ Cache failure → fallback to DB  
✅ One service instance down → load balancer routes elsewhere  
✅ Database failover configured  

System favors **availability over consistency**.

---

## 16️⃣ Trade‑Offs (Interview Gold)

### Why not store everything in cache?
❌ Cache is volatile  

### Why not avoid database?
❌ Data durability required  

### Why 302 instead of 301?
✅ Avoid permanent browser caching  

---

## 17️⃣ Security Considerations

✅ Rate limiting (prevent abuse)  
✅ Input validation (malicious URLs)  
✅ HTTPS everywhere  

---

## 18️⃣ How to Explain This in an Interview (Perfect Answer)

> “URL shortener is read‑heavy. We generate a short code using Base62, store mappings in DB, cache hot entries for fast redirects, and scale stateless services horizontally. Cache protects DB, and system remains highly available.”

This answer is:
✅ Structured  
✅ Complete  
✅ Senior‑level  

---

## 19️⃣ Key Takeaways

✅ Read‑heavy systems need aggressive caching  
✅ Short code generation is central design choice  
✅ Database is source of truth  
✅ Simplicity beats over‑engineering  

> **A good URL shortener design optimizes redirects, not creation.**

---

## What’s Next

Now that you’ve done a classic problem,
the next case study increases complexity with **controlled traffic**.

➡️ **Rate Limiter System Design**
