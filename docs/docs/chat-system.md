
# Chat / Messaging System – System Design Case Study (WhatsApp / Slack Style)

A **Chat or Messaging System** enables real‑time communication between users.
It is one of the most **challenging system design problems** because it combines:
- Real‑time delivery
- High availability
- Ordering guarantees
- Scalability
- Persistence

This case study focuses on designing a **reliable, scalable, real‑time messaging system**.

---

## 1️⃣ Problem Statement

Design a chat system that:
- Allows users to send messages
- Delivers messages in near real time
- Stores messages reliably
- Supports one‑to‑one chat (initially)

Examples:
- WhatsApp
- Messenger
- Slack (basic DM)

---

## 2️⃣ Functional Requirements

✅ One‑to‑one messaging  
✅ Messages delivered in near real time  
✅ Messages stored for offline users  
✅ Messages delivered in order  

Optional (clarify in interview):
- Group chats?
- Read receipts?
- Typing indicators?
- Media messages?

---

## 3️⃣ Non‑Functional Requirements

✅ Low latency  
✅ High availability  
✅ Scalability  
✅ Durability (no message loss)  

Interview insight:
> **Users tolerate slight delivery delay but not message loss or reordering.**

---

## 4️⃣ Scale Assumptions (High Level)

Example:
- Millions of users
- Thousands of messages per second
- Bursty traffic
- Users connected from mobile and web

---

## 5️⃣ Key Design Challenges

> **How do we deliver messages reliably and in order at scale?**

Key challenges:
- Online vs offline users
- Message ordering
- Fan‑out
- Connection management

---

## 6️⃣ High‑Level Architecture

```

Client (Web / Mobile)
|
v
Load Balancer
|
v
Chat Gateway (WebSocket)
|
+--> Message Service
|
+--> Message Queue
|
+--> Persistence Service

```

---

## 7️⃣ Why WebSockets?

REST polling is inefficient for chat.

WebSockets:
✅ Persistent connection  
✅ Low latency  
✅ Two‑way communication  

Used for:
- Real‑time message delivery
- Presence updates

---

## 8️⃣ Core Components Explained

### 1️⃣ Chat Gateway

- Maintains WebSocket connections
- Routes messages to backend services
- Tracks connected users

Stateless gateways allow horizontal scaling.

---

### 2️⃣ Message Service

Responsible for:
✅ Message validation  
✅ Message routing  
✅ Message acknowledgment  

This is the **core brain** of the system.

---

### 3️⃣ Message Queue

Why queue?
✅ Decouple sending from persistence  
✅ Handle burst traffic  
✅ Guarantee delivery  

Queue ensures:
> **At‑least‑once message delivery**

---

### 4️⃣ Persistence Service

Stores messages in durable storage.

Purpose:
✅ Offline access  
✅ Message history  
✅ Reliability  

---

## 9️⃣ Message Data Model (Simple)

```

## MESSAGES

message\_id
sender\_id
receiver\_id
content
timestamp
status (SENT / DELIVERED / READ)

```

---

## 10️⃣ Message Sending Flow

1. Sender sends message via WebSocket
2. Chat Gateway forwards to Message Service
3. Message written to queue
4. Message persisted in DB
5. Message delivered to receiver if online
6. ACK sent back to sender

✅ Sender gets confirmation  
✅ Message is never lost  

---

## 11️⃣ Handling Offline Users

If receiver is offline:
✅ Message stored in DB  
✅ Delivery attempted when user reconnects  

Offline capability is **mandatory** in chat systems.

---

## 12️⃣ Message Ordering (Critical)

Ordering is guaranteed:
✅ Per conversation  

Approach:
- Assign monotonically increasing sequence numbers
- Preserve order during delivery

Global ordering is unnecessary and expensive.

---

## 13️⃣ Idempotency & Duplicates

Due to retries:
❌ Duplicate messages possible  

Solution:
✅ Use message_id  
✅ Deduplicate at receiver side  

---

## 14️⃣ Read and Delivery Receipts

### Delivery Receipt
✅ Message reached receiver device  

### Read Receipt
✅ Message opened/read  

Receipts are optional extensions but common in real systems.

---

## 15️⃣ Scalability Strategy

✅ Stateless services  
✅ Horizontal scaling  
✅ Partition users by user_id  
✅ Partition queues by conversation  

The system scales linearly.

---

## 16️⃣ Failure Handling

### Gateway failure
✅ Client reconnects to another instance  

### Service failure
✅ Messages buffered in queue  

### Database failure
✅ Replicas / failover  

System favors **durability over immediacy**.

---

## 17️⃣ Consistency Guarantees

✅ At‑least‑once delivery  
✅ Per‑conversation ordering  
✅ Eventual consistency  

Strong consistency is not required.

---

## 18️⃣ Security Considerations

✅ Authentication on connection  
✅ Encrypted transport (TLS)  
✅ Access control per conversation  

End‑to‑end encryption can be added later.

---

## 19️⃣ Trade‑Offs (Interview Gold)

### Why WebSockets?
✅ Low latency  
❌ Connection management complexity  

### Why queues?
✅ Reliability & scalability  
❌ Increased latency  

### Why eventual consistency?
✅ High availability  
❌ Temporary inconsistencies  

---

## 20️⃣ Interview‑Ready Explanation (Use This)

> “Chat systems use persistent connections like WebSockets for real‑time delivery, queues for durability, and databases for persistence. Messages are ordered per conversation and delivered with at‑least‑once guarantees to ensure reliability at scale.”

This answer is **clear, confident, and senior‑level** ✅

---

## 21️⃣ Key Takeaways

✅ Real‑time systems require persistent connections  
✅ Durability is more important than instant delivery  
✅ Ordering is per chat, not global  
✅ Queues enable reliability  

> **A good chat system never loses messages, even during failures.**

---

## What’s Next

You now have designed:
✅ URL Shortener  
✅ Rate Limiter  
✅ Notification System  
✅ File Storage System  
✅ News Feed System  
✅ Chat / Messaging System 
