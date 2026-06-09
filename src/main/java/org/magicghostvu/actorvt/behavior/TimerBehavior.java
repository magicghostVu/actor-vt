package org.magicghostvu.actorvt.behavior;

import org.magicghostvu.actorvt.context.timer.TimerManData;

public final class TimerBehavior<T> extends Behavior<T> {

    public final ActorVTFunc<TimerManData<T>, Behavior<T>> timerFunc;

    TimerBehavior(ActorVTFunc<TimerManData<T>, Behavior<T>> timerFunc) {
        this.timerFunc = timerFunc;
    }
}
