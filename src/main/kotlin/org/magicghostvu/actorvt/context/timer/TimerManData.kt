package org.magicghostvu.actorvt.context.timer

import org.magicghostvu.actorvt.context.NormalActorContext
import org.slf4j.LoggerFactory
import java.util.concurrent.Future


//not thread-safe
//các hàm của timerMandata phải được gọi bên trong actor
class TimerManData<T> internal constructor(
    private val context: NormalActorContext<T>
) {
    private val idToTimerData = mutableMapOf<Any, TimerData>()
    private val idToGeneration = mutableMapOf<Any, Int>()

    private val logger = LoggerFactory.getLogger("actor-timer")

    // todo: thêm các hàm start single timer/ schedule ...
    //  cancel with key, cancel all...

    internal fun keyExist(key: Any): Boolean {
        return idToGeneration.containsKey(key)
    }

    internal fun getCurrentGeneration(key: Any): Int? {
        return idToGeneration[key]
    }

    internal fun removeKey(key: Any, cancelJob: Boolean) {
        /*if (idToTimerData.containsKey(key)) {
            val timerData = idToTimerData.getValue(key)
            if (cancelJob) {
                timerData.job.cancel()
            }
        }*/


        val timerData = idToTimerData[key]
        if (timerData != null && cancelJob) {
            timerData.job.cancel(true)
        }

        idToTimerData.remove(key)
        idToGeneration.remove(key)
    }

    internal fun getTimerData(key: Any): TimerData {
        return idToTimerData.getValue(key)
    }

    fun startSingleTimer(key: Any, message: T, delayMillis: Long): TimerData {
        removeKey(key, true)
        // phải lấy generation trước launch
        // nếu trong launch sẽ bị data race

        val expectGeneration = idToGeneration.compute(key) { _, oldValue ->
            if (oldValue == null) {
                0
            } else {
                oldValue + 1
            }
        } ?: throw IllegalArgumentException("can not be here")
        val messageToSend = DelayedMessage(
            message,
            key,
            expectGeneration
        )

        val job = context.actorSystem.threadPool.submit {
            Thread.sleep(delayMillis)
            //logger.info("exe timer")
            context.messageQueue.put(
                messageToSend
            )
        }
        //idToJob[key] = job
        val res = SingleTimerData(key, job, this)
        idToTimerData[key] = res
        return res
    }

    fun startFixedRateTimer(key: Any, message: T, initDelayMilli: Long, period: Long): TimerData {
        removeKey(key, true)
        // phải lấy generation trước launch
        // nếu trong launch sẽ bị data race

        val expectGeneration = idToGeneration.compute(key) { _, oldValue ->
            if (oldValue == null) {
                0
            } else {
                oldValue + 1
            }
        } ?: throw IllegalArgumentException("can not be here")
        val messageToSend = DelayedMessage(
            message,
            key,
            expectGeneration
        )
        val job: Future<*> = context.actorSystem.threadPool.submit {
            if (initDelayMilli > 0) {
                Thread.sleep(initDelayMilli)
            }
            while (true) {
                context.messageQueue.put(messageToSend)
                Thread.sleep(period)
            }
        }


        val res = PeriodicTimerData(key, job, this)
        idToTimerData[key] = res
        return res
    }


    fun startSingleTimer(message: T, delayMillis: Long): TimerData {
        return startSingleTimer(message as Any, message, delayMillis)
    }


    fun startFixedRateTimer(message: T, initDelay: Long, period: Long): TimerData {
        return startFixedRateTimer(message as Any, message, initDelay, period)
    }


    fun cancelAll() {
        idToTimerData.values.forEach {
            it.job.cancel(true)
        }
        idToTimerData.clear()
        idToGeneration.clear()
        logger.debug("cancel all called")
    }

    fun cancel(key: Any) {
        removeKey(key, true)
        logger.debug("timer with key {} canceled", key)
    }

}

internal data class DelayedMessage<T>(val message: T, val keyTimer: Any, val generationAtCreate: Int)