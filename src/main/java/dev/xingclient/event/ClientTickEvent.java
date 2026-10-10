package dev.xingclient.event;

public record ClientTickEvent(Phase phase) implements ClientEvent {
    public ClientTickEvent {
        if (phase == null) {
            throw new NullPointerException("phase");
        }
    }

    public enum Phase {
        START,
        END
    }
}
