package libraryagent.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import libraryagent.persistence.DataStore;

/** Shared fixtures: a seeded in-memory store and a clock tests can move forward. */
final class ServiceTestSupport {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");

    /** A clock fixed at a date that tests can advance. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(LocalDate date) {
            this.now = date.atTime(10, 0).atZone(ZONE).toInstant();
        }

        void advanceDays(long days) {
            now = now.plusSeconds(days * 86_400);
        }

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final LocalDate START = LocalDate.of(2026, 10, 23);

    static MutableClock clock() {
        return new MutableClock(START);
    }

    static DataStore seededStore(Clock clock) {
        return DataStore.inMemory(clock, true);
    }

    private ServiceTestSupport() {
    }
}
