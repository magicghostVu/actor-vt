package org.magicghostvu.actorvt.context.timer;

import java.util.concurrent.Future;

public final class SingleTimerData extends TimerData {

    SingleTimerData(Object key, Future<?> job, TimerManData<?> timerMan, int generation) {
        super(key, job, timerMan, generation);
    }
}
