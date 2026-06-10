package payment;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * PaymentSystem — System design for a reliable payment processing platform.
 *
 * SYSTEM DESIGN INTERVIEW: "Design a Payment Processing System"
 * ─────────────────────────────────────────────────────────────────────
 * Requirements:
 *   Functional:
 *     - Accept payments (credit card, UPI, wallet)
 *     - Idempotent API — retries never double-charge
 *     - Payment state machine: INITIATED → PROCESSING → SUCCESS/FAILED/REFUNDED
 *     - Reconciliation: detect and fix discrepancies with payment gateways
 *
 *   Non-Functional:
 *     - Exactly-once payment processing (hardest requirement)
 *     - < 3 second end-to-end
 *     - 99.99% availability (4 nines = 52 min downtime/year)
 *     - Audit trail for every state transition
 *     - PCI-DSS compliance (never store raw card numbers)
 *
 * KEY DESIGN CHALLENGES:
 * ─────────────────────────────────────────────────────────────────────
 *  1. Double-spend: race condition where same money deducted twice
 *     Fix: Idempotency key + atomic DB transaction with SELECT FOR UPDATE
 *
 *  2. Network failure between you and payment gateway:
 *     Fix: Payment state machine — never assume success, poll gateway for status
 *
 *  3. Distributed transaction: debit wallet + create order atomically?
 *     Fix: Saga pattern with compensating transactions
 *
 *  4. Card numbers: never store raw — tokenise with Stripe/Braintree
 *
 * Author: Mohit Kumar — github.com/Mohit-Java-Caps
 */
public class PaymentSystem {

    // ── Payment state machine ─────────────────────────────────────────────
    enum PaymentState {
        INITIATED,    // Payment record created, not yet sent to gateway
        PROCESSING,   // Sent to gateway, awaiting response
        SUCCESS,      // Gateway confirmed charge
        FAILED,       // Gateway declined or timed out
        REFUNDED,     // Refund processed
        DISPUTED      // Chargeback raised by customer
    }

    enum PaymentMethod { CREDIT_CARD, UPI, WALLET }

    // ── Domain models ─────────────────────────────────────────────────────
    static class Payment {
        final String id;
        final String idempotencyKey; // client-generated UUID per logical payment
        final String userId;
        final double amount;
        final String currency;
        final PaymentMethod method;
        volatile PaymentState state;
        volatile String gatewayRef;   // gateway's transaction ID
        volatile String failureReason;
        final long createdAt;
        final List<StateEvent> auditLog = new ArrayList<>();

        Payment(String id, String idempotencyKey, String userId,
                double amount, PaymentMethod method) {
            this.id             = id;
            this.idempotencyKey = idempotencyKey;
            this.userId         = userId;
            this.amount         = amount;
            this.currency       = "INR";
            this.method         = method;
            this.state          = PaymentState.INITIATED;
            this.createdAt      = System.currentTimeMillis();
            addAuditEvent(PaymentState.INITIATED, "Payment record created");
        }

        synchronized void transitionTo(PaymentState newState, String reason) {
            PaymentState old = this.state;
            if (!isValidTransition(old, newState)) {
                throw new IllegalStateException(
                    "Invalid transition: " + old + " → " + newState);
            }
            this.state = newState;
            addAuditEvent(newState, reason);
        }

        private void addAuditEvent(PaymentState state, String detail) {
            auditLog.add(new StateEvent(state, detail, System.currentTimeMillis()));
        }

        private boolean isValidTransition(PaymentState from, PaymentState to) {
            return switch (from) {
                case INITIATED   -> to == PaymentState.PROCESSING || to == PaymentState.FAILED;
                case PROCESSING  -> to == PaymentState.SUCCESS || to == PaymentState.FAILED;
                case SUCCESS     -> to == PaymentState.REFUNDED || to == PaymentState.DISPUTED;
                case FAILED      -> false; // terminal state
                case REFUNDED    -> false; // terminal state
                case DISPUTED    -> to == PaymentState.REFUNDED;
            };
        }

        void printAuditLog() {
            System.out.println("  Audit log for " + id.substring(0, 8) + ":");
            auditLog.forEach(e -> System.out.printf("    [%-12s] %s — %s%n",
                e.state(), e.detail(),
                new Date(e.timestamp()).toString().substring(11, 19)));
        }
    }

    record StateEvent(PaymentState state, String detail, long timestamp) {}

    // ── Wallet (in-memory, production: PostgreSQL with row-level locking) ─
    static class WalletService {
        private final ConcurrentHashMap<String, AtomicLong> balances = new ConcurrentHashMap<>();
        // Amount stored in paise (smallest unit) to avoid floating point issues

        void credit(String userId, long amountPaise) {
            balances.computeIfAbsent(userId, k -> new AtomicLong(100_000_00L)) // ₹1 lakh default
                    .addAndGet(amountPaise);
        }

        boolean debit(String userId, long amountPaise) {
            AtomicLong balance = balances.computeIfAbsent(userId, k -> new AtomicLong(100_000_00L));
            long current, newBalance;
            do {
                current    = balance.get();
                newBalance = current - amountPaise;
                if (newBalance < 0) return false; // insufficient funds
            } while (!balance.compareAndSet(current, newBalance)); // atomic CAS — no race condition
            return true;
        }

        double getBalance(String userId) {
            return balances.computeIfAbsent(userId, k -> new AtomicLong(100_000_00L)).get() / 100.0;
        }
    }

    // ── Idempotency store (Redis in production) ────────────────────────────
    static class IdempotencyStore {
        private final Map<String, String> store = new ConcurrentHashMap<>(); // key → paymentId

        Optional<String> get(String key) { return Optional.ofNullable(store.get(key)); }
        void put(String key, String paymentId) { store.put(key, paymentId); }
    }

    // ── Payment Gateway mock ───────────────────────────────────────────────
    static class PaymentGateway {
        private final Random rng = new Random();

        GatewayResponse charge(String paymentId, double amount, PaymentMethod method) {
            // Simulate gateway latency
            double roll = rng.nextDouble();
            if (roll < 0.85)      return new GatewayResponse(true,  "GW-" + paymentId.substring(0,6), null);
            else if (roll < 0.95) return new GatewayResponse(false, null, "Card declined by issuer");
            else                  return new GatewayResponse(false, null, "Gateway timeout");
        }

        GatewayResponse refund(String gatewayRef, double amount) {
            return new GatewayResponse(true, "REFUND-" + gatewayRef, null);
        }
    }

    record GatewayResponse(boolean success, String gatewayRef, String errorMessage) {}

    // ── Payment Service — orchestrates the whole flow ──────────────────────
    static class PaymentService {
        private final Map<String, Payment> payments = new ConcurrentHashMap<>();
        private final IdempotencyStore     idempotency;
        private final WalletService        wallet;
        private final PaymentGateway       gateway;
        private final AtomicLong           idGen = new AtomicLong(1000);

        PaymentService(IdempotencyStore idempotency, WalletService wallet, PaymentGateway gateway) {
            this.idempotency = idempotency;
            this.wallet      = wallet;
            this.gateway     = gateway;
        }

        Payment initiatePayment(String idempotencyKey, String userId,
                                double amount, PaymentMethod method) {
            // IDEMPOTENCY CHECK — if same key seen before, return existing payment
            Optional<String> existing = idempotency.get(idempotencyKey);
            if (existing.isPresent()) {
                Payment existingPayment = payments.get(existing.get());
                System.out.println("  [IDEMPOTENCY] Duplicate request detected — returning existing payment");
                return existingPayment;
            }

            // Create new payment record
            String paymentId = "PAY-" + idGen.getAndIncrement();
            Payment payment  = new Payment(paymentId, idempotencyKey, userId, amount, method);
            payments.put(paymentId, payment);
            idempotency.put(idempotencyKey, paymentId);

            System.out.printf("  Created payment %s | ₹%.2f | %s%n",
                paymentId, amount, method);

            // Process based on method
            if (method == PaymentMethod.WALLET) {
                processWalletPayment(payment);
            } else {
                processGatewayPayment(payment);
            }

            return payment;
        }

        private void processWalletPayment(Payment payment) {
            payment.transitionTo(PaymentState.PROCESSING, "Wallet debit initiated");
            long amountPaise = (long)(payment.amount * 100);

            if (wallet.debit(payment.userId, amountPaise)) {
                payment.gatewayRef = "WALLET-" + payment.id;
                payment.transitionTo(PaymentState.SUCCESS, "Wallet debited successfully");
            } else {
                payment.failureReason = "Insufficient wallet balance";
                payment.transitionTo(PaymentState.FAILED, payment.failureReason);
            }
        }

        private void processGatewayPayment(Payment payment) {
            payment.transitionTo(PaymentState.PROCESSING, "Sent to payment gateway");
            GatewayResponse response = gateway.charge(payment.id, payment.amount, payment.method);

            if (response.success()) {
                payment.gatewayRef = response.gatewayRef();
                payment.transitionTo(PaymentState.SUCCESS,
                    "Gateway approved — ref: " + response.gatewayRef());
            } else {
                payment.failureReason = response.errorMessage();
                payment.transitionTo(PaymentState.FAILED, response.errorMessage());
            }
        }

        Payment refund(String paymentId) {
            Payment payment = payments.get(paymentId);
            if (payment == null) throw new IllegalArgumentException("Payment not found");
            payment.transitionTo(PaymentState.REFUNDED, "Refund initiated by customer");
            gateway.refund(payment.gatewayRef, payment.amount);
            System.out.println("  Refund processed for " + paymentId);
            return payment;
        }

        void printAllPayments() {
            System.out.println("[PAYMENTS] Summary:");
            payments.values().forEach(p ->
                System.out.printf("  %-12s | ₹%7.2f | %-8s | %s%n",
                    p.id, p.amount, p.method, p.state));
        }
    }

    // ── Main ──────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("=== Payment System Design Demo ===\n");

        IdempotencyStore idempotency = new IdempotencyStore();
        WalletService    wallet      = new WalletService();
        PaymentGateway   gateway     = new PaymentGateway();
        PaymentService   service     = new PaymentService(idempotency, wallet, gateway);

        System.out.println("━━━ Scenario 1: Normal UPI Payment ━━━");
        String key1 = UUID.randomUUID().toString();
        Payment p1  = service.initiatePayment(key1, "user-01", 1499.00, PaymentMethod.UPI);
        System.out.println("  Final state: " + p1.state);
        p1.printAuditLog();

        System.out.println("\n━━━ Scenario 2: Idempotency — same payment submitted twice ━━━");
        System.out.println("  First request:");
        String key2 = UUID.randomUUID().toString();
        service.initiatePayment(key2, "user-02", 999.00, PaymentMethod.CREDIT_CARD);
        System.out.println("  Retry (same idempotency key):");
        Payment p2  = service.initiatePayment(key2, "user-02", 999.00, PaymentMethod.CREDIT_CARD);
        System.out.println("  Final state: " + p2.state + " (processed once, not twice ✓)");

        System.out.println("\n━━━ Scenario 3: Wallet Payment ━━━");
        System.out.printf("  Wallet balance before: ₹%.2f%n", wallet.getBalance("user-03"));
        Payment p3 = service.initiatePayment(UUID.randomUUID().toString(),
            "user-03", 500.00, PaymentMethod.WALLET);
        System.out.printf("  Wallet balance after: ₹%.2f%n", wallet.getBalance("user-03"));
        System.out.println("  Final state: " + p3.state);

        System.out.println("\n━━━ Scenario 4: Refund ━━━");
        if (p1.state == PaymentState.SUCCESS) {
            service.refund(p1.id);
            System.out.println("  Final state: " + p1.state);
            p1.printAuditLog();
        }

        System.out.println("\n━━━ Scenario 5: Invalid state transition (rejected) ━━━");
        try {
            p1.transitionTo(PaymentState.SUCCESS, "Hack attempt");
        } catch (IllegalStateException e) {
            System.out.println("  Rejected correctly: " + e.getMessage());
        }

        System.out.println("\n"); service.printAllPayments();

        System.out.println("""

            === Architecture Notes ===
            Idempotency: Redis SET NX with idempotency key, TTL 24h.
            Double-spend: SELECT FOR UPDATE on wallet row before debit (pessimistic lock).
            State machine: enforced at DB level via CHECK constraint + application layer.
            Audit log: append-only event table (payment_events) — never update, only insert.
            Reconciliation: nightly job compares our DB with gateway's settlement file.
            PCI-DSS: never store raw card — tokenise with Stripe/Braintree vault.
            Failure recovery: polling job checks PROCESSING payments > 30s → query gateway.
            """);
    }
}
