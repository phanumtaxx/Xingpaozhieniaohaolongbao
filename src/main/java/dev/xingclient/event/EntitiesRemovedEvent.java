package dev.xingclient.event;

import java.util.List;

/** Raised after the server's entity removals have been applied. Author: uint32. */
public record EntitiesRemovedEvent(List<Integer> entityIds) implements ClientEvent {
    public EntitiesRemovedEvent {
        entityIds = List.copyOf(entityIds);
    }
}
