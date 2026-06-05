package org.magicghostvu.actorvt.behavior;

import org.magicghostvu.actorvt.context.GeneralActorContext;

public abstract non-sealed class AbstractBehavior<T> extends Behavior<T> {

    protected final GeneralActorContext<T> context;

    protected AbstractBehavior(GeneralActorContext<T> context) {
        this.context = context;
    }

    public abstract Behavior<T> onReceive(T message);
}
