
# Notification System – System Design Case Study

A **Notification System** is a classic real‑world system design problem.
It appears in many forms:
- Email notifications
- SMS alerts
- Push notifications
- In‑app messages

This case study focuses on designing a **scalable, reliable, asynchronous notification system** that works in production.

---

## 1️⃣ Problem Statement

Design a system that:
- Sends notifications to users
- Supports multiple channels (Email, SMS, Push)
- Handles high traffic
- Guarantees reliability (no lost notifications)

Examples:
- Order confirmation
- Password reset
- Promotional alerts
- Fraud warnings

---

## 2️⃣ Functional Requirements

✅ Send notifications to users  
✅ Support multiple notification channels  
✅ Allow asynchronous processing  
✅ Handle retries on failure  

Optional (clarify in interview):
- Priority levels?
- Scheduled notifications?
- User preferences?
- Analytics?

---

## 3️⃣ Non‑Functional Requirements

✅ High scalability  
✅ High availability  
✅ Fault tolerance  
✅ Low coupling between services  
✅ Eventual delivery  

Interview insight:
> **Notification systems prioritize reliability over immediate delivery.**

---

## 4️⃣ Scale Assumptions (High Level)

Example assumptions:
- Millions of users
- Tens of millions of notifications/day
- Burst traffic during events (sales, campaigns)

The system must handle:
✅ Sudden spikes  
✅ Slow downstream providers  

---

## 5️⃣ High‑Level Architecture

```

Client / Backend Service
|
v
Notification API
|
v
Message Queue
|
+--> Email Worker
+--> SMS Worker
+--> Push Worker

```

---

## 6️⃣ Why Asynchronous Architecture Is Required

If notifications were sent synchronously:
❌ User requests would block  
❌ Latency would increase  
❌ Failures would impact core flows  

Asynchronous processing:
✅ Decouples services  
✅ Improves reliability  
✅ Handles bursts naturally  

---

## 7️⃣ Core Components Explained

### 1️⃣ Notification API

- Accepts notification requests
- Validates input
- Publishes events to queue

It does NOT:
❌ Send messages directly  

---

### 2️⃣ Message Queue

The backbone of the system.

Purpose:
✅ Buffer requests  
✅ Absorb spikes  
✅ Enable retries  

Examples (conceptual):
- Kafka
- RabbitMQ
- SQS

Tool names matter less than **concept**.

---

### 3️⃣ Notification Workers

Separate worker services for:
- Email
- SMS
- Push notifications

Why separate workers?
✅ Independent scaling  
✅ Failure isolation  
✅ Channel‑specific logic  

---

## 8️⃣ Request Flow (Step‑by‑Step)

1. Event occurs (e.g., order placed)
2. Backend calls Notification API
3. Notification request goes to queue
4. Worker consumes message
5. External provider is called
6. Delivery result is recorded

✅ Core transaction is not blocked  

---

## 9️⃣ Database Design (Simple Model)

```

## NOTIFICATIONS

id
user\_id
channel (EMAIL/SMS/PUSH)
payload
status (PENDING/SENT/FAILED)
retry\_count
created\_at

```

Database is used to:
✅ Track state  
✅ Support retries  
✅ Enable audit  

---

## 🔁 Retry Mechanism (Critical)

External providers fail all the time.

Retry strategy:
✅ Retry with backoff  
✅ Limit retry count  
✅ Move to DLQ (dead‑letter queue) after max retries  

Never retry endlessly.

---

## 10️⃣ Handling Failures Gracefully

### Provider Down?
✅ Retry later  

### Worker Crashed?
✅ Message reprocessed  

### Queue Down?
✅ Requests buffered or failed fast  

This design ensures:
✅ At‑least‑once delivery  

---

## 11️⃣ Fan‑Out Problem (Interview Favorite)

Sending notification to **many users**.

Approach:
- Break into smaller messages
- Push individual jobs to queue
- Process in parallel

Avoid:
❌ One giant request  

---

## 12️⃣ User Preferences Handling

Users may:
- Disable email
- Enable only push
- Prefer certain time windows

Store preferences:
```

## USER\_PREFERENCES

user\_id
email\_enabled
sms\_enabled
push\_enabled

```

Workers must check preferences **before sending**.

---

## 13️⃣ Idempotency Considerations

Duplicate messages can happen.

Solution:
✅ Use notification ID
✅ Ensure same notification isn’t sent twice

Idempotency is **essential** in distributed systems.

---

## 14️⃣ Observability

Monitor:
✅ Queue length  
✅ Delivery failures  
✅ Retry counts  
✅ Processing latency  

Alerts prevent silent failures.

---

## 15️⃣ Scaling the System

✅ Stateless workers  
✅ Add consumers horizontally  
✅ Partition queues if needed  

System scales **linearly** with workers.

---

## 16️⃣ Trade‑Offs (Interview Gold)

### Why asynchronous?
✅ Reliability and scalability  
❌ Eventual delivery  

### Why queues?
✅ Decoupling  
✅ Burst handling  

### Why workers per channel?
✅ Isolation  
✅ Independent scaling  

---

## 17️⃣ Security Considerations

✅ Secure APIs  
✅ Encrypt sensitive payloads  
✅ Protect provider credentials  
✅ Rate limit notification requests  

---

## 18️⃣ Interview‑Ready Explanation (Use This)

> “Notification systems are built asynchronously using queues to decouple producers from delivery. Separate workers handle email, SMS, and push channels with retries and dead‑letter queues to ensure reliable delivery at scale.”

Clear. Calm. Senior‑level ✅

---

## 19️⃣ Key Takeaways

✅ Notifications must be asynchronous  
✅ Queues absorb spikes and failures  
✅ Workers isolate delivery logic  
✅ Reliability > immediacy  

> **Notification systems succeed when they never block and never lose messages.**

---

## What’s Next

Now that you’ve designed:
✅ URL Shortener
✅ Rate Limiter
✅ Notification System
