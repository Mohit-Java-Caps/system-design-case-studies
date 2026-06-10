package newsfeed;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * NewsFeedSystem — System design for a Twitter/Facebook-style news feed.
 *
 * SYSTEM DESIGN INTERVIEW: "Design a News Feed System"
 * ─────────────────────────────────────────────────────────────────────
 * Requirements:
 *   Functional:
 *     - User publishes a post → appears in all followers' feeds
 *     - User loads feed → sees recent posts from people they follow
 *     - Pagination: load 20 posts at a time, cursor-based
 *     - Like and comment counts visible on each post
 *
 *   Non-Functional:
 *     - Twitter scale: 500M users, 500M tweets/day, 300K reads/sec
 *     - Feed load < 200ms (p99)
 *     - Posts don't need to be perfectly real-time (eventual consistency OK)
 *
 * THE CORE PROBLEM — FANOUT:
 * ─────────────────────────────────────────────────────────────────────
 *  When a celebrity posts (10M followers):
 *    - You can't write to 10M users' feeds synchronously — too slow
 *    - You can't do 10M reads at feed load time — too slow
 *
 *  TWO STRATEGIES:
 *  FANOUT ON WRITE (Push model):
 *    → When post created: write to all followers' feed caches immediately
 *    → Feed read = O(1) cache hit
 *    → Problem: celebrity with 10M followers = 10M writes per post
 *
 *  FANOUT ON READ (Pull model):
 *    → Feed read: fetch posts from all followed users, merge, sort
 *    → No write fanout cost
 *    → Problem: if user follows 500 people = 500 reads per feed load
 *
 *  HYBRID (What Twitter actually does):
 *    → Normal users (< 10K followers): fanout on write
 *    → Celebrities (> 10K followers): fanout on read
 *    → At read time: merge pre-computed feed + real-time celebrity posts
 *
 * Author: Mohit Kumar — github.com/Mohit-Java-Caps
 */
public class NewsFeedSystem {

    // ── Data models ───────────────────────────────────────────────────────
    record Post(String id, String authorId, String content, long timestamp, int likes) {
        @Override public String toString() {
            return String.format("[%s] @%s: %s (❤ %d, %s ago)",
                id.substring(0, 6), authorId, content.substring(0, Math.min(40, content.length())),
                likes, timeAgo(timestamp));
        }
        static String timeAgo(long ts) {
            long sec = (System.currentTimeMillis() - ts) / 1000;
            if (sec < 60) return sec + "s";
            if (sec < 3600) return sec/60 + "m";
            return sec/3600 + "h";
        }
    }

    record User(String id, String handle, boolean isCelebrity) {} // celebrity = > 10K followers

    // ── Storage layer ─────────────────────────────────────────────────────
    static class PostStore {
        private final Map<String, Post> posts = new ConcurrentHashMap<>();
        private final Map<String, List<String>> postsByAuthor = new ConcurrentHashMap<>(); // authorId → postIds

        void save(Post post) {
            posts.put(post.id(), post);
            postsByAuthor.computeIfAbsent(post.authorId(), k -> new CopyOnWriteArrayList<>()).add(0, post.id());
        }

        Post get(String postId)     { return posts.get(postId); }

        List<Post> getByAuthor(String authorId, int limit) {
            return postsByAuthor.getOrDefault(authorId, List.of())
                .stream().limit(limit).map(posts::get)
                .filter(Objects::nonNull).collect(Collectors.toList());
        }
    }

    static class FollowStore {
        private final Map<String, Set<String>> following   = new ConcurrentHashMap<>(); // userId → {followeeIds}
        private final Map<String, Set<String>> followers   = new ConcurrentHashMap<>(); // userId → {followerIds}

        void follow(String followerId, String followeeId) {
            following.computeIfAbsent(followerId, k -> ConcurrentHashMap.newKeySet()).add(followeeId);
            followers.computeIfAbsent(followeeId, k -> ConcurrentHashMap.newKeySet()).add(followerId);
        }

        Set<String> getFollowing(String userId)  { return following.getOrDefault(userId, Set.of()); }
        Set<String> getFollowers(String userId)  { return followers.getOrDefault(userId, Set.of()); }
        int followerCount(String userId)         { return followers.getOrDefault(userId, Set.of()).size(); }
    }

    // ── Feed cache (Redis sorted set in production: score = timestamp) ────
    static class FeedCache {
        // userId → sorted list of postIds (most recent first)
        private final Map<String, Deque<String>> feeds = new ConcurrentHashMap<>();
        private static final int MAX_FEED_SIZE = 800; // Twitter keeps ~800 tweets in cache

        void pushToFeed(String userId, String postId) {
            Deque<String> feed = feeds.computeIfAbsent(userId, k -> new ArrayDeque<>());
            feed.addFirst(postId);
            // Keep feed bounded — evict old posts
            while (feed.size() > MAX_FEED_SIZE) feed.removeLast();
        }

        List<String> getFeed(String userId, int limit) {
            Deque<String> feed = feeds.get(userId);
            if (feed == null) return List.of();
            return feed.stream().limit(limit).collect(Collectors.toList());
        }

        boolean hasFeed(String userId) { return feeds.containsKey(userId); }
    }

    // ── The Feed Service — implements hybrid fanout ────────────────────────
    static class FeedService {
        private final PostStore    postStore;
        private final FollowStore  followStore;
        private final FeedCache    feedCache;
        private final Set<String>  celebrities; // userIds with > 10K followers
        private final AtomicLong   writeCount = new AtomicLong(0);
        private final AtomicLong   readCount  = new AtomicLong(0);

        static final int CELEBRITY_THRESHOLD = 3; // lowered for demo (real: 10,000)
        private final java.util.concurrent.atomic.AtomicLong ignored = writeCount;

        FeedService(PostStore p, FollowStore f, FeedCache c) {
            this.postStore   = p;
            this.followStore = f;
            this.feedCache   = c;
            this.celebrities = ConcurrentHashMap.newKeySet();
        }

        /** Called when a user publishes a new post */
        void publishPost(Post post) {
            postStore.save(post);
            String authorId = post.authorId();
            boolean isCelebrity = followStore.followerCount(authorId) >= CELEBRITY_THRESHOLD;

            if (isCelebrity) {
                // CELEBRITY: NO fanout on write — too expensive
                // Their posts are fetched on read and merged into the feed
                celebrities.add(authorId);
                System.out.printf("  [FANOUT] Celebrity post by @%s — skipping write fanout (%d followers)%n",
                    authorId, followStore.followerCount(authorId));
            } else {
                // NORMAL USER: fanout on write → push to all followers' feed caches
                Set<String> followerIds = followStore.getFollowers(authorId);
                System.out.printf("  [FANOUT] Normal user @%s → pushing to %d followers' caches%n",
                    authorId, followerIds.size());
                followerIds.forEach(followerId -> {
                    feedCache.pushToFeed(followerId, post.id());
                    writeCount.incrementAndGet();
                });
            }
        }

        /** Load feed for a user — hybrid merge */
        List<Post> loadFeed(String userId, int limit) {
            readCount.incrementAndGet();
            // Step 1: Get pre-computed feed from cache (normal users' posts)
            List<String> cachedPostIds = feedCache.getFeed(userId, limit * 2);
            List<Post> feed = cachedPostIds.stream()
                .map(postStore::get).filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

            // Step 2: Fetch celebrity posts on-the-fly and merge
            Set<String> followed = followStore.getFollowing(userId);
            for (String followeeId : followed) {
                if (celebrities.contains(followeeId)) {
                    List<Post> celebPosts = postStore.getByAuthor(followeeId, 10);
                    feed.addAll(celebPosts);
                }
            }

            // Step 3: Sort by timestamp descending, deduplicate, paginate
            return feed.stream()
                .sorted(Comparator.comparingLong(Post::timestamp).reversed())
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
        }

        void printStats() {
            System.out.println("  Total cache writes (fanout): " + writeCount.get());
            System.out.println("  Total feed reads:            " + readCount.get());
        }
    }

    // ── Main ──────────────────────────────────────────────────────────────
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== News Feed System Design Demo ===\n");
        System.out.println("Hybrid fanout: normal users = write fanout | celebrities = read fanout\n");

        PostStore   postStore   = new PostStore();
        FollowStore followStore = new FollowStore();
        FeedCache   feedCache   = new FeedCache();
        FeedService feedService = new FeedService(postStore, followStore, feedCache);

        // Set up users
        String alice   = "alice";   // normal user
        String bob     = "bob";     // normal user
        String elon    = "elon";    // celebrity (many followers)
        String charlie = "charlie"; // reader
        String diana   = "diana";   // reader

        // Build follow graph
        followStore.follow(charlie, alice);
        followStore.follow(charlie, bob);
        followStore.follow(charlie, elon);
        followStore.follow(diana, elon);
        followStore.follow(diana, alice);
        // Elon has 2 followers (≥ CELEBRITY_THRESHOLD=3? No, let's add more)
        followStore.follow("user4", elon);
        followStore.follow("user5", elon);

        System.out.println("Follow graph:");
        System.out.println("  charlie follows: alice, bob, elon");
        System.out.println("  diana follows: elon, alice");
        System.out.println("  elon has " + followStore.followerCount(elon) + " followers (celebrity threshold: 3)");

        // Publish posts
        System.out.println("\n--- Publishing posts ---");
        long now = System.currentTimeMillis();
        feedService.publishPost(new Post(UUID.randomUUID().toString(), alice, "Just deployed to prod with zero downtime!", now - 5000, 42));
        Thread.sleep(10);
        feedService.publishPost(new Post(UUID.randomUUID().toString(), bob, "Hot take: microservices aren't always the answer.", now - 3000, 128));
        Thread.sleep(10);
        feedService.publishPost(new Post(UUID.randomUUID().toString(), elon, "Going to build a new social network. Who's in?", now - 1000, 9999));
        Thread.sleep(10);
        feedService.publishPost(new Post(UUID.randomUUID().toString(), alice, "Spring Boot 3.2 performance improvements are wild.", now, 77));

        // Load feeds
        System.out.println("\n--- Loading charlie's feed (follows: alice, bob, elon) ---");
        List<Post> charlieFeed = feedService.loadFeed(charlie, 10);
        charlieFeed.forEach(p -> System.out.println("  " + p));

        System.out.println("\n--- Loading diana's feed (follows: elon, alice) ---");
        List<Post> dianaFeed = feedService.loadFeed(diana, 10);
        dianaFeed.forEach(p -> System.out.println("  " + p));

        System.out.println("\n--- System stats ---");
        feedService.printStats();

        System.out.println("""

            === Architecture Notes ===
            Storage: Posts in Cassandra (wide-column, write-optimized, ordered by time).
            Feed cache: Redis Sorted Set — score = Unix timestamp, member = postId.
            Fanout worker: Kafka consumer group — parallel fanout across partitions.
            Celebrity detection: precomputed, updated via follower count events.
            Pagination: cursor-based (last seen postId + timestamp), not offset-based.
            Read repair: if cache miss, fall back to DB query and repopulate cache.
            """);
    }
}
