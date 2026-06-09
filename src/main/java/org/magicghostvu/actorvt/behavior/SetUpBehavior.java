package org.magicghostvu.actorvt.behavior;

import org.magicghostvu.actorvt.context.GeneralActorContext;


public final class SetUpBehavior<T> extends Behavior<T> {

    public final ActorVTFunc<GeneralActorContext<T>, Behavior<T>> factory;

    SetUpBehavior(ActorVTFunc<GeneralActorContext<T>, Behavior<T>> factory) {
        this.factory = factory;
    }
}
