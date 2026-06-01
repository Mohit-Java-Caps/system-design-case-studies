package ratelimiter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * RateLimiterDesign — System design implementation of a distributed rate limiter.
 *
 * SYSTEM DESIGN INTERVIEW: "Design a Rate Limiter"
 * ─────────────────────────────────────────────────────────────────────
 * Requirements:
 *   Functional:
 *     - Limit requests per client (by API key / user ID / IP)
 *     - Support multiple strategies (token bucket, sliding window, fixed window)
 *     - Return remaining quota in response headers
 *     - Allow configurable limits per endpoint / tier
 *
 *   Non-Functional:
 *     - < 1ms overhead per request check
 *     - 99.99% availability (rate limiter can't be SPOF)
 *     - Accurate across distributed nodes (no double-spend)
 *
 * SCALE ESTIMATION:
 *   10M active users × 100 req/day = 1B requests/day = ~11,500 req/sec
 *   Metadata per client: ~100 bytes × 10M = 1 GB RAM → fits in Redis cluster
 *
 * ARCHITECTURE DECISION:
 *   - Store counters in Redis (centralized, atomic INCR, TTL support)
 *   - Use Lua scripts for atomic check-and-increment (no race conditions)
 *   - Fallback to local in-memory if Redis unavailable (fail-open strategy)
 *   - Edge nodes do local pre-check, Redis does authoritative check
 *
 * THREE ALGORITHMS IMPLEMENTED:
 *   1. Fixed Window   — simple, fast, allows 2x burst at boundary
 *   2. Sliding Window — accurate, higher memory
 *   3. Token Bucket   — allows controlled bursts, smooth sustained rate ← best for APIs
 *
 * Author: Mohit Kumar — github.com/Mohit-Java-Caps
 */
public class RateLimiterDesign {

    // ── Algorithm 1: Fixed Window ─────────────────────────────────────────
    static class FixedWindowRateLimiter {
        private final int limit;
        private final long windowMs;
        private final Map<String, long[]> windows = new ConcurrentHashMap<>();
        // long[0] = window start time, long[1] = request count

        FixedWindowRateLimiter(int limit, long windowMs) {
            this.limit    = limit;
            this.windowMs = windowMs;
        }

        synchronized boolean tryAcquire(String clientId) {
            long now  = System.currentTimeMillis();
            long[] w  = windows.computeIfAbsent(clientId, k -> new long[]{now, 0});

            // New window? Reset counter
            if (now - w[0] >= windowMs) {
                w[0] = now;
                w[1] = 0;
            }

            if (w[1] < limit) { w[1]++; return true; }
            return false;
        }

        // KNOWN FLAW: At window boundary, client can send 'limit' requests at end of
        // window + 'limit' at start of next = 2x burst. Sliding window fixes this.
    }

    // ── Algorithm 2: Sliding Window Log ──────────────────────────────────
    static class SlidingWindowRateLimiter {
        private final int limit;
        private final long windowMs;
        private final Map<String, Deque<Long>> logs = new ConcurrentHashMap<>();

        SlidingWindowRateLimiter(int limit, long windowMs) {
            this.limit    = limit;
            this.windowMs = windowMs;
        }

        synchronized boolean tryAcquire(String clientId) {
            long now    = System.currentTimeMillis();
            long cutoff = now - windowMs;

            Deque<Long> log = logs.computeIfAbsent(clientId, k -> new ArrayDeque<>());

            // Remove timestamps outside the window
            while (!log.isEmpty() && log.peekFirst() <= cutoff) log.pollFirst();

            if (log.size() < limit) {
                log.addLast(now);
                return true;
            }
            return false;
        }

        int requestsInWindow(String clientId) {
            Deque<Long> log = logs.get(clientId);
            return log == null ? 0 : log.size();
        }
    }

    // ── Algorithm 3: Token Bucket (best for production APIs) ─────────────
    static class TokenBucketRateLimiter {
        private final int capacity;
        private final double refillRatePerMs;
        private final Map<String, double[]> buckets = new ConcurrentHashMap<>();
        // double[0] = current tokens, double[1] = last refill timestamp

        TokenBucketRateLimiter(int capacity, int refillPerSecond) {
            this.capacity        = capacity;
            this.refillRatePerMs = refillPerSecond / 1000.0;
        }

        synchronized boolean tryAcquire(String clientId) {
            long now    = System.currentTimeMillis();
            double[] b  = buckets.computeIfAbsent(clientId,
                k -> new double[]{capacity, now});

            // Refill tokens based on elapsed time
            double elapsed = now - b[1];
            b[0] = Math.min(capacity, b[0] + elapsed * refillRatePerMs);
            b[1] = now;

            if (b[0] >= 1) {
                b[0]--;
                return true;
            }
            return false;
        }

        double remainingTokens(String clientId) {
            double[] b = buckets.get(clientId);
            return b == null ? capacity : b[0];
        }
    }

    // ── Tier-based config (real-world: different limits per subscription) ─
    enum ClientTier { FREE, PRO, ENTERPRISE }

    static class TieredRateLimiter {
        private final Map<ClientTier, TokenBucketRateLimiter> limiters = new EnumMap<>(ClientTier.class);

        TieredRateLimiter() {
            limiters.put(ClientTier.FREE,       new TokenBucketRateLimiter(10,  10));   // 10 req/sec, burst 10
            limiters.put(ClientTier.PRO,        new TokenBucketRateLimiter(100, 100));  // 100 req/sec, burst 100
            limiters.put(ClientTier.ENTERPRISE, new TokenBucketRateLimiter(1000,1000)); // 1000 req/sec, burst 1000
        }

        RateLimitResult check(String clientId, ClientTier tier) {
            TokenBucketRateLimiter limiter = limiters.get(tier);
            boolean allowed = limiter.tryAcquire(clientId);
            return new RateLimitResult(
                allowed,
                (int) limiter.remainingTokens(clientId),
                allowed ? "OK" : "429 Too Many Requests"
            );
        }
    }

    record RateLimitResult(boolean allowed, int remaining, String status) {}

    // ── Main ──────────────────────────────────────────────────────────────
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Rate Limiter System Design — All 3 Algorithms ===\n");

        // Fixed Window demo
        System.out.println("━━━ Fixed Window (5 req / 1 sec) ━━━");
        FixedWindowRateLimiter fixed = new FixedWindowRateLimiter(5, 1000);
        for (int i = 1; i <= 7; i++) {
            boolean ok = fixed.tryAcquire("user-1");
            System.out.printf("  Request %d: %s%n", i, ok ? "ALLOWED ✓" : "BLOCKED ✗ (429)");
        }

        // Sliding Window demo
        System.out.println("\n━━━ Sliding Window (5 req / 1 sec) ━━━");
        SlidingWindowRateLimiter sliding = new SlidingWindowRateLimiter(5, 1000);
        for (int i = 1; i <= 7; i++) {
            boolean ok = sliding.tryAcquire("user-2");
            System.out.printf("  Request %d: %-12s | In-window count: %d%n",
                i, ok ? "ALLOWED ✓" : "BLOCKED ✗", sliding.requestsInWindow("user-2"));
        }

        // Token Bucket demo
        System.out.println("\n━━━ Token Bucket (5 tokens capacity, 2 tokens/sec refill) ━━━");
        TokenBucketRateLimiter bucket = new TokenBucketRateLimiter(5, 2);
        for (int i = 1; i <= 7; i++) {
            boolean ok = bucket.tryAcquire("user-3");
            System.out.printf("  Request %d: %-12s | Remaining tokens: %.1f%n",
                i, ok ? "ALLOWED ✓" : "BLOCKED ✗", bucket.remainingTokens("user-3"));
        }
        System.out.println("  Waiting 2 seconds for token refill...");
        Thread.sleep(2000);
        System.out.printf("  After refill: %s | Tokens: %.1f%n",
            bucket.tryAcquire("user-3") ? "ALLOWED ✓" : "BLOCKED ✗",
            bucket.remainingTokens("user-3"));

        // Tiered rate limiter
        System.out.println("\n━━━ Tiered Rate Limiter (FREE vs PRO vs ENTERPRISE) ━━━");
        TieredRateLimiter tiered = new TieredRateLimiter();
        String[] clients = {"free-user", "pro-user", "enterprise-user"};
        ClientTier[] tiers = {ClientTier.FREE, ClientTier.PRO, ClientTier.ENTERPRISE};
        for (int i = 0; i < 3; i++) {
            RateLimitResult r = tiered.check(clients[i], tiers[i]);
            System.out.printf("  %-20s [%-12s]: %s | Remaining: %d%n",
                clients[i], tiers[i], r.status(), r.remaining());
        }

        System.out.println("""

            === Architecture Notes ===
            Distributed: Redis INCR + EXPIRE for atomic counter + TTL.
            Lua script: atomically check + increment (prevents race conditions).
            Headers to return: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
            Fail-open vs fail-closed: if Redis down, allow traffic (fail-open) to avoid outage.
            Real libraries: Resilience4j RateLimiter, Bucket4j (Redis-backed).
            """);
    }
}
