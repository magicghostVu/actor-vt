package org.magicghostvu.actorvt.behavior;

import org.magicghostvu.actorvt.context.timer.TimerManData;

import java.util.function.Function;

public final class TimerBehavior<T> extends Behavior<T> {

    public final Function<TimerManData<T>, Behavior<T>> timerFunc;

    TimerBehavior(Function<TimerManData<T>, Behavior<T>> timerFunc) {
        this.timerFunc = timerFunc;
    }
}
