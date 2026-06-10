package notification;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NotificationSystem — System design for a scalable multi-channel notification service.
 *
 * SYSTEM DESIGN INTERVIEW: "Design a Notification System"
 * ─────────────────────────────────────────────────────────────────────
 * Requirements:
 *   Functional:
 *     - Send notifications via: Push (FCM/APNs), Email (SendGrid), SMS (Twilio)
 *     - Support: real-time, scheduled, bulk notifications
 *     - User preferences: opt-in/opt-out per channel per notification type
 *     - Deduplication: same notification never sent twice to the same user
 *     - Priority: CRITICAL (OTP, fraud alert) > HIGH > NORMAL > LOW
 *
 *   Non-Functional:
 *     - 10M notifications/day = ~116/sec sustained, 1000/sec peak
 *     - CRITICAL: < 1 second delivery
 *     - NORMAL:   < 5 seconds
 *     - Retry on failure with exponential backoff
 *     - At-least-once delivery guarantee
 *
 * HIGH-LEVEL DESIGN:
 *   API → Kafka Topic (partitioned by userId) → Notification Workers
 *       → Channel Router (email/push/SMS) → 3rd party providers
 *       → Delivery status DB (PostgreSQL) → Retry queue (Redis)
 *
 * KEY DESIGN DECISIONS:
 *   1. Kafka for fan-out: one event → multiple channels
 *   2. Priority queues: CRITICAL consumers process before NORMAL
 *   3. Idempotency key: notificationId → deduplicate at worker level
 *   4. User preference cache: Redis (TTL 1h) → avoid DB per notification
 *   5. Retry: DLQ (Dead Letter Queue) for failed after 3 attempts
 *
 * Author: Mohit Kumar — github.com/Mohit-Java-Caps
 */
public class NotificationSystem {

    enum Channel   { PUSH, EMAIL, SMS }
    enum Priority  { CRITICAL, HIGH, NORMAL, LOW }
    enum Status    { PENDING, SENT, FAILED, DEDUPLICATED }

    // ── Notification model ────────────────────────────────────────────────
    record Notification(
        String id,         // UUID — used for deduplication
        String userId,
        String title,
        String body,
        Priority priority,
        List<Channel> channels,
        long scheduledAt   // 0 = send now
    ) {}

    record DeliveryResult(String notificationId, Channel channel, Status status, String detail) {}

    // ── User preferences store ────────────────────────────────────────────
    static class UserPreferenceStore {
        // userId → set of opted-out channels
        private final Map<String, Set<Channel>> optOuts = new ConcurrentHashMap<>();

        void optOut(String userId, Channel channel) {
            optOuts.computeIfAbsent(userId, k -> new HashSet<>()).add(channel);
            System.out.printf("[PREFS] User %s opted out of %s%n", userId, channel);
        }

        boolean isAllowed(String userId, Channel channel) {
            Set<Channel> blocked = optOuts.get(userId);
            return blocked == null || !blocked.contains(channel);
        }
    }

    // ── Deduplication store (Redis in production) ─────────────────────────
    static class DeduplicationStore {
        private final Set<String> processedIds = ConcurrentHashMap.newKeySet();

        boolean isDuplicate(String notificationId, String userId, Channel channel) {
            String key = notificationId + ":" + userId + ":" + channel;
            return !processedIds.add(key); // returns false if newly added (not a dup)
        }
    }

    // ── Channel senders (3rd party integrations) ──────────────────────────
    static class PushSender {
        private final Random rng = new Random();
        DeliveryResult send(String userId, String title, String body) {
            // Simulate FCM/APNs call — 95% success rate
            boolean success = rng.nextDouble() > 0.05;
            String detail   = success ? "FCM token accepted" : "Device token expired";
            return new DeliveryResult("", Channel.PUSH,
                success ? Status.SENT : Status.FAILED, detail);
        }
    }

    static class EmailSender {
        DeliveryResult send(String userId, String title, String body) {
            // Simulate SendGrid — 99% success
            return new DeliveryResult("", Channel.EMAIL, Status.SENT,
                "Delivered to " + userId + "@example.com");
        }
    }

    static class SmsSender {
        private final Random rng = new Random();
        DeliveryResult send(String userId, String body) {
            // Simulate Twilio — 98% success
            boolean success = rng.nextDouble() > 0.02;
            return new DeliveryResult("", Channel.SMS,
                success ? Status.SENT : Status.FAILED,
                success ? "SMS delivered to +91-XXXXXX" + userId.hashCode() % 10000
                        : "Number unreachable");
        }
    }

    // ── Priority queue (simulates Kafka with priority topics) ─────────────
    static class PriorityNotificationQueue {
        // One queue per priority (CRITICAL processes first)
        private final Map<Priority, BlockingQueue<Notification>> queues = new EnumMap<>(Priority.class);

        PriorityNotificationQueue() {
            for (Priority p : Priority.values())
                queues.put(p, new LinkedBlockingQueue<>());
        }

        void enqueue(Notification n) {
            queues.get(n.priority()).offer(n);
            System.out.printf("[QUEUE] Enqueued [%-8s] notification %s for user %s%n",
                n.priority(), n.id().substring(0, 8), n.userId());
        }

        // Poll highest priority first
        Notification poll() {
            for (Priority p : Priority.values()) {
                Notification n = queues.get(p).poll();
                if (n != null) return n;
            }
            return null;
        }

        int size() {
            return queues.values().stream().mapToInt(BlockingQueue::size).sum();
        }
    }

    // ── Notification Worker ───────────────────────────────────────────────
    static class NotificationWorker {
        private final UserPreferenceStore prefs;
        private final DeduplicationStore  dedup;
        private final PushSender  pushSender  = new PushSender();
        private final EmailSender emailSender = new EmailSender();
        private final SmsSender   smsSender   = new SmsSender();
        private final List<DeliveryResult> log = new ArrayList<>();
        private final AtomicLong retryCount   = new AtomicLong(0);

        NotificationWorker(UserPreferenceStore prefs, DeduplicationStore dedup) {
            this.prefs = prefs;
            this.dedup = dedup;
        }

        void process(Notification n) {
            System.out.printf("%n[WORKER] Processing [%-8s] notification: %s%n",
                n.priority(), n.title());

            for (Channel channel : n.channels()) {
                // Check user preference
                if (!prefs.isAllowed(n.userId(), channel)) {
                    System.out.printf("  [%-5s] SKIPPED — user opted out%n", channel);
                    log.add(new DeliveryResult(n.id(), channel, Status.DEDUPLICATED, "User opt-out"));
                    continue;
                }

                // Check deduplication
                if (dedup.isDuplicate(n.id(), n.userId(), channel)) {
                    System.out.printf("  [%-5s] DEDUPLICATED — already sent%n", channel);
                    log.add(new DeliveryResult(n.id(), channel, Status.DEDUPLICATED, "Duplicate"));
                    continue;
                }

                // Send via appropriate channel (with retry)
                DeliveryResult result = sendWithRetry(n, channel, 3);
                System.out.printf("  [%-5s] %s — %s%n", channel, result.status(), result.detail());
                log.add(result);
            }
        }

        private DeliveryResult sendWithRetry(Notification n, Channel channel, int maxRetries) {
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                DeliveryResult result = switch (channel) {
                    case PUSH  -> pushSender.send(n.userId(), n.title(), n.body());
                    case EMAIL -> emailSender.send(n.userId(), n.title(), n.body());
                    case SMS   -> smsSender.send(n.userId(), n.body());
                };

                if (result.status() == Status.SENT) {
                    if (attempt > 1) retryCount.incrementAndGet();
                    return new DeliveryResult(n.id(), channel, Status.SENT, result.detail());
                }

                if (attempt < maxRetries) {
                    long backoff = (long)(Math.pow(2, attempt) * 100); // 200ms, 400ms
                    System.out.printf("  [%-5s] Attempt %d failed. Retrying in %dms...%n",
                        channel, attempt, backoff);
                }
            }
            // After maxRetries: send to DLQ (Dead Letter Queue)
            System.out.printf("  [%-5s] All retries exhausted → DLQ%n", channel);
            return new DeliveryResult(n.id(), channel, Status.FAILED, "Moved to DLQ after " + maxRetries + " attempts");
        }

        void printSummary() {
            long sent  = log.stream().filter(r -> r.status() == Status.SENT).count();
            long dedup = log.stream().filter(r -> r.status() == Status.DEDUPLICATED).count();
            long fail  = log.stream().filter(r -> r.status() == Status.FAILED).count();
            System.out.println("\n[SUMMARY] Notifications processed:");
            System.out.println("  SENT:          " + sent);
            System.out.println("  DEDUPLICATED:  " + dedup);
            System.out.println("  FAILED (DLQ):  " + fail);
            System.out.println("  Retries made:  " + retryCount.get());
        }
    }

    // ── Main ──────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("=== Notification System Design Demo ===\n");

        UserPreferenceStore prefs  = new UserPreferenceStore();
        DeduplicationStore  dedup  = new DeduplicationStore();
        PriorityNotificationQueue queue = new PriorityNotificationQueue();
        NotificationWorker worker  = new NotificationWorker(prefs, dedup);

        // User opts out of SMS
        prefs.optOut("user-42", Channel.SMS);

        // Create notifications with different priorities
        List<Notification> notifications = List.of(
            new Notification(UUID.randomUUID().toString(), "user-01",
                "🚨 Fraud Alert", "Suspicious login from new device. Was this you?",
                Priority.CRITICAL, List.of(Channel.PUSH, Channel.SMS, Channel.EMAIL), 0),

            new Notification(UUID.randomUUID().toString(), "user-42",
                "Order Shipped", "Your order #ORD-789 has shipped. Track here.",
                Priority.HIGH, List.of(Channel.PUSH, Channel.EMAIL, Channel.SMS), 0),

            new Notification(UUID.randomUUID().toString(), "user-99",
                "Weekly Digest", "Here's what you missed this week...",
                Priority.LOW, List.of(Channel.EMAIL), 0)
        );

        // Enqueue all
        System.out.println("--- Enqueuing notifications ---");
        notifications.forEach(queue::enqueue);

        // Process (CRITICAL first, then HIGH, then LOW — regardless of enqueue order)
        System.out.println("\n--- Processing (priority order) ---");
        Notification n;
        while ((n = queue.poll()) != null) {
            worker.process(n);
        }

        // Deduplication test — send same notification again
        System.out.println("\n--- Deduplication test (resending same notification) ---");
        worker.process(notifications.get(0)); // same ID = deduplicated

        worker.printSummary();

        System.out.println("""

            === Architecture Notes ===
            Fan-out: Kafka topic "notifications" → partitioned by userId for ordering.
            Priority: separate Kafka topics (critical-notifications, normal-notifications).
            User prefs: cached in Redis (TTL 1h) — avoid DB call per notification.
            Deduplication: Redis SET with notificationId+userId+channel key, TTL 24h.
            Bulk: batch processor reads from DB, publishes to Kafka in chunks of 1000.
            Analytics: delivery events → Kafka → Flink → ClickHouse → Grafana dashboard.
            """);
    }
}
