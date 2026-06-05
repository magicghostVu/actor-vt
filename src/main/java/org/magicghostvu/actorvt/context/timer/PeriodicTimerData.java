package org.magicghostvu.actorvt.context.timer;

import java.util.concurrent.Future;

public final class PeriodicTimerData extends TimerData {

    PeriodicTimerData(Object key, Future<?> job, TimerManData<?> timerMan, int generation) {
        super(key, job, timerMan, generation);
    }
}
