package org.magicghostvu.actorvt.behavior;

import org.magicghostvu.actorvt.context.GeneralActorContext;

import java.util.function.Function;

public final class SetUpBehavior<T> extends Behavior<T> {

    public final Function<GeneralActorContext<T>, Behavior<T>> factory;

    SetUpBehavior(Function<GeneralActorContext<T>, Behavior<T>> factory) {
        this.factory = factory;
    }
}
