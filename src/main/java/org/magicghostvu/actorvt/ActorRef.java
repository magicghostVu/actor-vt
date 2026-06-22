package org.magicghostvu.actorvt;

import org.jspecify.annotations.Nullable;
import org.magicghostvu.actorvt.context.GeneralActorContext;

public final class ActorRef<T> {

    public final String name;
    public final String path;

    @Nullable
    public volatile GeneralActorContext<T> context;


    public ActorRef(String name, GeneralActorContext<T> context) {
        this.name = name;
        this.context = context;
        this.path = context.getPath();
    }

    public void tell(T message) {
        GeneralActorContext<T> c = context;
        if (c != null && c.active) {
            try {
                c.messageQueue.put(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while sending to actor " + name, e);
            }
        } else {
            throw new IllegalArgumentException(String.format(
                    "actor %s with path %s not ready to receive msg (active: %s)",
                    name, path, c != null ? c.active : "null"));
        }
    }

    public boolean trySend(T message) {
        GeneralActorContext<T> c = context;
        if (c != null && c.active) {
            try {
                return c.messageQueue.offer(message);
            } catch (IllegalStateException ie) {
                return false;
            }
        } else throw new IllegalArgumentException(String.format(
                "actor %s with path %s not ready to receive msg (active: %s)",
                name, path, c != null ? c.active : "null"));

    }
}
