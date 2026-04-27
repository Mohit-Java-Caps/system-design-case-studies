
# 🧠 System Design Cheat Sheet (1‑Page, Interview Ready)

***

## ✅ 1️⃣ How to Start Any System Design Interview

**Always follow this order (say it implicitly):**

1.  Clarify requirements
2.  Separate functional vs non‑functional
3.  Estimate scale (rough numbers)
4.  High‑level architecture
5.  Data model
6.  Bottlenecks & failures
7.  Trade‑offs

> **Golden rule:** Structure > complexity

***

## ✅ 2️⃣ Core Non‑Functional Requirements (Always Name These)

*   **Scalability** – horizontal over vertical
*   **Availability** – system still works during failures
*   **Latency** – user‑visible speed
*   **Durability** – no data loss
*   **Consistency** – depends on business criticality

> Say: *“I’ll prioritize based on business needs.”*

***

## ✅ 3️⃣ Read vs Write Patterns (Very Important)

| System Type   | Dominant Traffic |
| ------------- | ---------------- |
| URL Shortener | Read‑heavy       |
| News Feed     | Read‑heavy       |
| Search        | Read‑heavy       |
| Chat          | Balanced         |
| Payments      | Write‑critical   |
| Notifications | Write spikes     |

**Design follows traffic, not tools.**

***

## ✅ 4️⃣ Cache Usage Rules

✅ Cache **reads**, not writes  
✅ Cache **hot data**  
❌ Cache ≠ source of truth

Common caches:

*   URL redirects
*   Feeds
*   Search results
*   Rate‑limit counters

> *Cache improves latency, DB ensures correctness.*

***

## ✅ 5️⃣ Asynchronous vs Synchronous

| Use Case      | Preferred                 |
| ------------- | ------------------------- |
| Payments      | Sync + async confirmation |
| Notifications | Async                     |
| Feeds         | Async fan‑out             |
| File uploads  | Async/chunked             |
| Analytics     | Async                     |

> **Async = scalability + reliability**

***

## ✅ 6️⃣ Queues – When and Why

Use queues when:

*   Traffic spikes
*   External dependencies
*   Retry required
*   Decoupling needed

Examples:

*   Notifications
*   Chat messages
*   Payment confirmation
*   Feed updates

> Queues absorb pressure — they don’t remove it.

***

## ✅ 7️⃣ Idempotency (CRITICAL)

Required when:

*   Retries possible
*   Money involved
*   Network unreliable

Used in:

*   Payments
*   Webhooks
*   Message delivery

✅ Use unique request / payment IDs  
❌ Never retry blindly

***

## ✅ 8️⃣ Consistency Choices (Interview Favorite)

| System        | Consistency |
| ------------- | ----------- |
| Payments      | Strong      |
| Chat          | Eventual    |
| Feed          | Eventual    |
| Search        | Eventual    |
| Notifications | Eventual    |

> **Consistency is a business decision, not a default choice.**

***

## ✅ 9️⃣ Fan‑Out Patterns

### Fan‑Out on Write

✅ Fast reads  
❌ Expensive writes

### Fan‑Out on Read

✅ Cheap writes  
❌ Slow reads

✅ **Hybrid is most common** (feeds, timelines)

***

## ✅ 1️⃣0️⃣ Storage Choices (High‑Level)

| Data        | Storage        |
| ----------- | -------------- |
| Metadata    | Databases      |
| Large files | Object storage |
| Counters    | Redis          |
| Messages    | Logs / queues  |
| Search      | Inverted index |

> Separate storage by access pattern.

***

## ✅ 1️⃣1️⃣ Failure Handling (Always Mention)

*   Service crash → restart / failover
*   Cache down → fallback to DB
*   Queue buildup → autoscale consumers
*   External failure → retry + circuit breaker

> **Assume everything fails. Design recovery.**

***

## ✅ 1️⃣2️⃣ CAP Theorem (Simple Use)

*   **CP** → Payments
*   **AP** → Feeds, Search
*   **CA** → Single‑node systems

Don’t lecture. Mention briefly.

***

## ✅ 1️⃣3️⃣ Ranking / Ordering Rules

*   Chat → per conversation ordering
*   Feed → time or relevance
*   Search → relevance score
*   Recommendations → offline + online

Global ordering is **rarely needed**.

***

## ✅ 1️⃣4️⃣ Interview Red Flags (AVOID)

❌ “Use Kafka everywhere”  
❌ “Perfect consistency”  
❌ “We can optimize later”  
❌ Tool dumping  
❌ No trade‑offs

***

## ✅ 1️⃣5️⃣ Golden Interview Lines (Memorize)

*   “I’ll start simple and iterate.”
*   “This system favors availability over consistency.”
*   “Most traffic is read‑heavy, so caching is critical.”
*   “Correctness matters more than latency here.”
*   “Failures are expected; recovery matters.”

***

## ✅ 1️⃣6️⃣ System → Core Pattern Mapping

| System          | Core Pattern                |
| --------------- | --------------------------- |
| URL Shortener   | Cache + DB                  |
| Rate Limiter    | Token bucket + Redis        |
| Notifications   | Queue + workers             |
| Chat            | WebSocket + queue           |
| Feed            | Hybrid fan‑out              |
| Payments        | Idempotency + state machine |
| Search          | Inverted index              |
| Recommendations | Offline + online            |
| File Storage    | Metadata + object store     |

***

## ✅ 1️⃣7️⃣ Final Mental Model

> **System design is not about drawing boxes.  
> It’s about explaining why each box exists.**

***

### ✅ How to Use This Cheat Sheet

*   Read it **once a day** before interviews
*   Don’t memorize — understand patterns
*   Speak calmly, structure first, adapt later

