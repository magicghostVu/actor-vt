package org.magicghostvu.actorvt.context.timer

import java.util.concurrent.Future

sealed class TimerData(private val key: Any, val job: Future<*>, private val timerMan: TimerManData<*>) {
    public fun cancel() {
        timerMan.removeKey(key, true)
    }
}

// khi message đến/hoặc quá hạn thì xoá
class SingleTimerData internal constructor(key: Any, job: Future<*>, timerMan: TimerManData<*>) :
    TimerData(key, job, timerMan)


// xoá khi bị đè hoặc là bị huỷ
class PeriodicTimerData internal constructor(key: Any, job: Future<*>, timerMan: TimerManData<*>) :
    TimerData(key, job, timerMan)