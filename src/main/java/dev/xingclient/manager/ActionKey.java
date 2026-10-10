package dev.xingclient.manager;

import java.util.concurrent.atomic.AtomicLong;

public final class ActionKey {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final long id = NEXT_ID.incrementAndGet();
    long id() { return id; }
}
