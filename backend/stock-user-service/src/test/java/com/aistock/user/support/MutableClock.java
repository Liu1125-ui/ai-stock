package com.aistock.user.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> time;
    private final ZoneId zone;
    public MutableClock(Instant start) { this(new AtomicReference<>(start), ZoneOffset.UTC); }
    private MutableClock(AtomicReference<Instant> time, ZoneId zone) { this.time = time; this.zone = zone; }
    public void advance(Duration duration) { time.updateAndGet(current -> current.plus(duration)); }
    @Override public Instant instant() { return time.get(); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId requested) { return new MutableClock(time, requested); }
}
