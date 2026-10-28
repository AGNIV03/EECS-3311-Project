package libraryagent.ui.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Sliding-window limits per key (e.g. per visitor IP): at most N per minute and M per hour. */
final class RateLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private final int perMinute;
    private final int perHour;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();

    RateLimiter(int perMinute, int perHour, Clock clock) {
        this.perMinute = perMinute;
        this.perHour = perHour;
        this.clock = clock;
    }

    /** Records a hit and returns true, or returns false if the key is over a limit. */
    synchronized boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Deque<Instant> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(HOUR))) {
            times.pollFirst();
        }
        long lastMinute = times.stream().filter(t -> t.isAfter(now.minus(MINUTE))).count();
        if (lastMinute >= perMinute || times.size() >= perHour) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    /** Forgets keys with no recent hits. */
    synchronized void sweep() {
        Instant cutoff = clock.instant().minus(HOUR);
        hits.values().removeIf(times -> times.isEmpty() || times.peekLast().isBefore(cutoff));
    }
}
