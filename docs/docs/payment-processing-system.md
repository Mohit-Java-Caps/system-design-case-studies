
# Payment Processing System – System Design Case Study

A **Payment Processing System** handles money, which means:
- Correctness is more important than speed
- Failures must be handled carefully
- Duplicate charges must never happen
- Every action must be auditable

This is one of the **most critical and high‑signal system design interview questions**.

---

## 1️⃣ Problem Statement

Design a payment system that:
- Allows users to make payments
- Processes payments using external gateways
- Guarantees no duplicate charges
- Handles failures and retries safely

Examples:
- E‑commerce checkout
- Subscription payments
- Wallet transfers

---

## 2️⃣ Functional Requirements

✅ Initiate a payment  
✅ Process payment via external provider  
✅ Update payment status (SUCCESS / FAILED / PENDING)  
✅ Prevent duplicate charges  

Optional (clarify in interview):
- Refunds?
- Partial payments?
- Recurring subscriptions?
- Multiple payment providers?

---

## 3️⃣ Non‑Functional Requirements

✅ Strong consistency  
✅ High reliability  
✅ Idempotency  
✅ Auditability  
✅ Security  

Interview insight:
> **Payment systems favor correctness over low latency.**

---

## 4️⃣ Scale Assumptions (High Level)

Example:
- Thousands of payments per second
- Spikes during sales
- External payment gateway latency
- Network and provider failures are expected

---

## 5️⃣ Core Design Challenges

> **How do we ensure that money is charged exactly once, even with retries and failures?**

Key challenges:
- Idempotency
- External dependency reliability
- Distributed state management
- Failure recovery

---

## 6️⃣ High‑Level Architecture

```

Client
|
v
API Gateway
|
v
Payment Service
|
+--> Payment DB
|
+--> Message Queue
|
+--> Payment Gateway (External)

```

---

## 7️⃣ Key Design Concepts (Very Important)

Before diving deeper, understand these principles:

✅ **Idempotency is mandatory**  
✅ **State must be persisted before external calls**  
✅ **Retries must be safe**  
✅ **Async processing improves reliability**

---

## 8️⃣ Payment State Machine

A payment always moves through known states:

```

CREATED → PROCESSING → SUCCESS
└→ FAILED

```

State transitions must be:
✅ Validated  
✅ Atomic  
✅ Auditable  

---

## 9️⃣ Database Design (Simplified)

```

## PAYMENTS

payment\_id (idempotency key)
user\_id
amount
currency
status
provider\_reference
created\_at
updated\_at

```

Important:
✅ `payment_id` must be unique  
✅ Used as idempotency key  

---

## 10️⃣ Idempotency (Critical Interview Topic)

Clients may retry requests due to:
- Network timeout
- Gateway delay
- App crash

Without idempotency:
❌ Duplicate charges  
❌ Serious financial issues  

Solution:
> **Use an idempotency key for every payment request.**

Same request + same key → same result.

---

## 11️⃣ Payment Request Flow (Step‑by‑Step)

1. Client initiates payment with idempotency key
2. Payment Service checks DB
3. If payment exists:
   ✅ Return existing result
4. Else:
   - Create payment record (CREATED)
   - Persist state
5. Call external gateway
6. Update payment status (SUCCESS / FAILED)

---

## 12️⃣ Why Persist Before Calling Gateway?

This is a **huge interview insight**.

If the service crashes:
✅ Payment record already exists  
✅ Retry resumes safely  

Never call payment gateway **before persisting intent**.

---

## 13️⃣ Handling External Payment Gateways

Payment gateways are:
❌ Slow  
❌ Unreliable  
❌ Outside your control  

Design assumptions:
✅ Gateway calls can timeout  
✅ Gateway may return unknown status  

---

## 14️⃣ Retry Strategy

Use:
✅ Timeouts  
✅ Retries with backoff  
✅ Circuit breaker  

Retry is applied to:
✅ Status checks  
✅ Webhook verification  

Never blindly retry charge creation.

---

## 15️⃣ Webhooks (Very Important)

Payment gateways often send webhooks:

```

Gateway → Payment Service (Payment succeeded)

```

Webhooks:
✅ Confirm final payment status  
✅ Are asynchronous  
✅ Must be idempotent  

Webhook handling:
- Validate signature
- Match payment_id
- Update status safely

---

## 16️⃣ Asynchronous Processing

Why async?
✅ Decouple user request from final confirmation  
✅ Improve resilience  
✅ Handle delayed gateway responses  

Queue ensures:
✅ No payment is lost  

---

## 17️⃣ Failure Scenarios & Handling

### Crash before gateway response
✅ Check payment status later  

### Gateway timeout
✅ Poll gateway or wait for webhook  

### Duplicate client request
✅ Idempotency key protects system  

---

## 18️⃣ Refunds (High Level)

Refund is:
✅ A new payment flow  
✅ With its own lifecycle  

Never modify original payment amount.

---

## 19️⃣ Security Considerations

✅ Encrypt sensitive data  
✅ Never store raw card details  
✅ Secure webhook endpoints  
✅ Strong authentication & authorization  

Security mistakes in payment systems are catastrophic.

---

## 20️⃣ Trade‑Offs (Interview Gold)

### Why async processing?
✅ Reliability  
❌ Slightly delayed confirmation  

### Why idempotency keys?
✅ Exactly‑once semantics  
❌ Extra state management  

### Why persist first?
✅ Crash safety  
❌ Slight initial latency  

---

## 21️⃣ Interview‑Ready Explanation (Use This)

> “Payment systems use idempotency keys and persistent state to guarantee exactly‑once charging. Payment intent is stored before calling external gateways, async processing and webhooks handle eventual confirmation, and retries are carefully controlled.”

This answer signals **high maturity** ✅

---

## 22️⃣ Key Takeaways

✅ Payments must be idempotent  
✅ Correctness > speed  
✅ External gateways will fail  
✅ Persist before calling external systems  
✅ Every action must be auditable  

> **In payment systems, a slow success is better than a fast mistake.**

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
