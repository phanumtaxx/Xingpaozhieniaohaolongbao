package dev.xingclient.manager;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ActionOwner {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final long id = NEXT_ID.incrementAndGet();
    private final String name;

    public ActionOwner(String name) { this.name = Objects.requireNonNull(name); }
    public long id() { return id; }
    @Override public String toString() { return name; }
}
