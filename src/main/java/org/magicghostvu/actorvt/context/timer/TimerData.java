package org.magicghostvu.actorvt.context.timer;

import java.util.concurrent.Future;

public sealed abstract class TimerData permits SingleTimerData, PeriodicTimerData {

    private final Object key;
    final Future<?> job;
    private final TimerManData<?> timerMan;
    public final int generation;

    TimerData(Object key, Future<?> job, TimerManData<?> timerMan, int generation) {
        this.key = key;
        this.job = job;
        this.timerMan = timerMan;
        this.generation = generation;
    }

    public void cancel() {
        timerMan.removeKey(key, true);
    }
}
