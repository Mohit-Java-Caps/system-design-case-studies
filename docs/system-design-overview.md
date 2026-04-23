# System Design – How to Think, Not Just What to Draw

System design interviews are not about getting a “correct architecture”.
They are about demonstrating **clear, structured thinking** while handling trade‑offs.

This document explains **how to approach system design problems step by step**.

---

## What Is System Design?

> **System design is the process of defining architecture, components, data flow, and trade‑offs to build software systems that meet functional and non‑functional requirements.**

In practice, it answers:
- How does the system work?
- How does it scale?
- How does it fail?
- How does it recover?

---

## The Biggest System Design Mistake

❌ Jumping straight into components

Wrong approach:
> “Let’s use Kafka, Redis, Cassandra, and microservices.”

Correct approach:
> “Let’s understand the problem and constraints first.”

---

## Step‑by‑Step System Design Approach (Interview Gold)

Always follow this sequence.

---

### 1️⃣ Clarify the Problem

Ask questions:
- Who are the users?
- How many users?
- What is the core use case?

Never assume — clarify.

---

### 2️⃣ Define Functional Requirements

These describe **what the system must do**.

Example:
✅ Create short URLs  
✅ Redirect to original URL  

---

### 3️⃣ Define Non‑Functional Requirements

These describe **how well the system must perform**.

Example:
✅ High availability  
✅ Low latency  
✅ Scalability  
✅ Durability  

These drive architectural decisions.

---

### 4️⃣ Estimate Scale (High Level)

Understand:
- Requests per second
- Read vs write ratio
- Data growth

This guides:
✅ Database choice  
✅ Caching  
✅ Sharding  

---

### 5️⃣ High‑Level Architecture

Draw big components:
- Clients
- Services
- Databases
- Caches

Avoid details initially.

---

### 6️⃣ Data Model

Design:
- Entities
- Relationships
- Indexing strategy

Bad data model = bad system.

---

### 7️⃣ Bottlenecks & Failure Handling

Ask:
- What breaks first?
- What if a component goes down?
- How do we recover?

This separates juniors from seniors.

---

### 8️⃣ Trade‑Offs & Alternatives

There is no perfect design.

Explain:
✅ Why you chose one approach  
✅ What you gain  
✅ What you sacrifice  

Interviewers love honest trade‑offs.

---

## Key Interview Insight

> **Clarity beats complexity.  
Reasoning beats buzzwords.  
Trade‑offs beat perfection.**

---

## What’s Next

Now that you understand the **process**, we’ll apply it to real systems.

➡️ First case study:
📄 **URL Shortener (TinyURL)**
