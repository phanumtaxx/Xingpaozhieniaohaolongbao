package dev.xingclient.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Synchronous event dispatch for client-thread events. */
public final class EventBus {
    private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();

    public <E extends ClientEvent> Subscription subscribe(Class<E> eventType, Consumer<? super E> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(listener);
        return () -> remove(eventType, listener);
    }

    public void post(ClientEvent event) {
        Objects.requireNonNull(event, "event");
        List<Consumer<?>> registered = listeners.get(event.getClass());
        if (registered == null) {
            return;
        }

        for (Consumer<?> listener : List.copyOf(registered)) {
            dispatch(listener, event);
        }
    }

    private <E extends ClientEvent> void dispatch(Consumer<?> listener, ClientEvent event) {
        @SuppressWarnings("unchecked")
        Consumer<E> typedListener = (Consumer<E>) listener;
        @SuppressWarnings("unchecked")
        E typedEvent = (E) event;
        typedListener.accept(typedEvent);
    }

    private <E extends ClientEvent> void remove(Class<E> eventType, Consumer<? super E> listener) {
        List<Consumer<?>> registered = listeners.get(eventType);
        if (registered == null) {
            return;
        }

        registered.remove(listener);
        if (registered.isEmpty()) {
            listeners.remove(eventType);
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
