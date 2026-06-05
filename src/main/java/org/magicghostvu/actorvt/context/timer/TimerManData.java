package org.magicghostvu.actorvt.context.timer;

import org.jspecify.annotations.Nullable;
import org.magicghostvu.actorvt.context.GeneralActorContext;
import org.magicghostvu.actorvt.context.msg.DelayMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public final class TimerManData<T> {

    private final GeneralActorContext<T> context;
    private final Map<Object, TimerData> idToTimerData = new HashMap<>();
    // Global counter — each new registration gets a unique generation stamp.
    // Never resets, so a stale DelayMsg from any previous registration of a key
    // will always carry an older generation than the current TimerData.
    private int nextGeneration = 0;
    private final Logger logger = LoggerFactory.getLogger("actor-timer");

    public TimerManData(GeneralActorContext<T> context) {
        this.context = context;
    }

    // Returns the currently registered TimerData for key, or null if none.
    // Called from GeneralActorContext.onMsgCome to validate incoming DelayMsgs.
    @Nullable
    public TimerData findTimerData(Object key) {
        return idToTimerData.get(key);
    }

    // cancelJob=true  — explicit cancel or FROM_PARENT stop: interrupt the job.
    // cancelJob=false — single-timer natural fire (called before onReceive so the
    //                   actor can restart the same key inside its handler).
    public void removeKey(Object key, boolean cancelJob) {
        TimerData timerData = idToTimerData.remove(key);
        if (timerData != null && cancelJob) {
            timerData.job.cancel(true);
        }
    }

    public TimerData startSingleTimer(Object key, T message, long delayMillis) {
        TimerData existing = idToTimerData.remove(key);
        if (existing != null) {
            existing.job.cancel(true);
        }
        int gen = nextGeneration++;
        var messageToSend = new DelayMsg<>(message, key, gen);
        Future<?> job = context.actorSystem.threadPool.submit((Callable<Void>) () -> {
            Thread.sleep(delayMillis);
            context.messageQueue.put(messageToSend);
            return null;
        });
        var res = new SingleTimerData(key, job, this, gen);
        idToTimerData.put(key, res);
        return res;
    }

    public TimerData startFixedRateTimer(Object key, T message, long initDelayMillis, long period) {
        TimerData existing = idToTimerData.remove(key);
        if (existing != null) {
            existing.job.cancel(true);
        }
        int gen = nextGeneration++;
        var messageToSend = new DelayMsg<>(message, key, gen);
        Future<?> job = context.actorSystem.threadPool.submit((Callable<Void>) () -> {
            if (initDelayMillis > 0) {
                Thread.sleep(initDelayMillis);
            }
            //noinspection InfiniteLoopStatement
            while (true) {
                context.messageQueue.put(messageToSend);
                Thread.sleep(period);
            }
        });
        var res = new PeriodicTimerData(key, job, this, gen);
        idToTimerData.put(key, res);
        return res;
    }

    public TimerData startSingleTimer(T message, long delayMillis) {
        return startSingleTimer((Object) message, message, delayMillis);
    }

    public TimerData startFixedRateTimer(T message, long initDelay, long period) {
        return startFixedRateTimer((Object) message, message, initDelay, period);
    }

    public void cancelAll() {
        for (TimerData data : idToTimerData.values()) {
            data.job.cancel(true);
        }
        idToTimerData.clear();
        logger.debug("cancel all called");
    }

    public void cancel(Object key) {
        removeKey(key, true);
        logger.debug("timer with key {} canceled", key);
    }
}
