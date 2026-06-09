package org.magicghostvu.actorvt.behavior;

import org.magicghostvu.actorvt.context.GeneralActorContext;
import org.magicghostvu.actorvt.context.timer.TimerManData;

public final class Behaviors {

    private Behaviors() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Behavior<T> same() {
        return (Behavior<T>) Same.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public static <T> Behavior<T> stopped() {
        return (Behavior<T>) Stopped.INSTANCE;
    }

    public static <T> Behavior<T> setUp(ActorVTFunc<GeneralActorContext<T>, Behavior<T>> factory) {
        return new SetUpBehavior<>(factory);
    }

    public static <T> Behavior<T> withTimer(ActorVTFunc<TimerManData<T>, Behavior<T>> timerFunc) {
        return new TimerBehavior<>(timerFunc);
    }
}
