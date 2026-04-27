1️⃣ “Can you explain how you approach a system design problem?”

Spoken Answer


“I start by first clarifying the problem and requirements instead of jumping into architecture. I separate functional requirements from non‑functional ones like scalability, latency, and availability.
Then I estimate the scale at a high level to understand traffic and data volume.
After that, I propose a simple high‑level design, define data models, identify bottlenecks, and finally discuss trade‑offs and failure scenarios.
I prioritize clarity and reasoning over over‑engineering.”

✅ This answer shows structure, maturity, and calm thinking.

2️⃣ “Design a URL Shortener” (Spoken Summary)

Spoken Answer


“A URL shortener is a read‑heavy system.
I generate a short code using a Base62 encoding of a unique ID and store the mapping in a database.
For redirects, I use aggressive caching because read traffic is much higher than writes.
The database is the source of truth, while cache improves latency.
The system is stateless and scales horizontally, and availability is favored over strict consistency.”

✅ Interviewer hears: scale awareness + caching logic + trade‑offs

3️⃣ “How does a Rate Limiter work in a distributed system?”

Spoken Answer


“Rate limiting controls request frequency to protect backend systems.
In distributed environments, I usually implement it at the API Gateway using a token bucket algorithm backed by Redis.
Redis allows atomic operations and shared state across instances.
The system favors availability over perfect accuracy, and on failure we either fail open or fail closed depending on use case.”

✅ Strong signal: practical distributed reasoning

4️⃣ “How would you design a Notification System?”

Spoken Answer


“Notification systems are asynchronous by design.
Requests are accepted quickly and pushed to a queue, while worker services handle delivery through email, SMS, or push providers.
Queues absorb spikes, enable retries, and isolate failures.
We track delivery state in a database and use retry with backoff and dead‑letter queues for reliability.”

✅ Shows event‑driven and reliability thinking

5️⃣ “How would you design a Chat System?”

Spoken Answer


“Chat systems use persistent connections like WebSockets for low‑latency delivery.
Messages are sent through a gateway, persisted using queues to ensure durability, and delivered to receivers if they’re online.
Ordering is maintained per conversation, not globally.
The system guarantees at‑least‑once delivery and prefers durability over immediate latency.”

✅ Very senior‑level explanation.

6️⃣ “How do News Feed systems scale?”

Spoken Answer


“The core decision is fan‑out on write versus fan‑out on read.
Fan‑out on write gives fast reads but high write amplification, while fan‑out on read simplifies writes but slows reads.
Most real systems use a hybrid approach: push posts for normal users and pull posts from celebrities at read time.
Caching is essential because feed systems are read‑heavy.”

✅ Interviewers LOVE this answer.

7️⃣ “How do payment systems avoid duplicate charges?”

Spoken Answer


“Payment systems rely on idempotency keys and persistent state.
We store the payment intent before calling external gateways, so retries are safe.
Even if the system crashes or the gateway times out, we can resume or verify payment using stored state or webhooks.
Correctness is always prioritized over speed.”

✅ Extremely high‑signal answer.

8️⃣ “How do Search systems work at scale?”

Spoken Answer


“Search systems separate primary data storage from search indexing.
Data changes are asynchronously indexed using inverted indexes optimized for keyword lookup.
Reads dominate traffic, so caching and distributed indexes are critical.
Eventual consistency is acceptable because availability and speed are more important.”

✅ Clean and confident.

9️⃣ “How do Recommendation Engines work?”

Spoken Answer


“Recommendation systems are split into offline and online layers.
Offline batch jobs analyze large volumes of user behavior to generate recommendation candidates.
The online layer serves these recommendations quickly with lightweight ranking and filters.
The system balances relevance, latency, and scalability rather than perfect accuracy.”

✅ Signals ML‑adjacent but system‑focused maturity.
