package org.magicghostvu.actorvt.context;

import org.jspecify.annotations.Nullable;
import org.magicghostvu.actorvt.ActorRef;
import org.magicghostvu.actorvt.behavior.*;
import org.magicghostvu.actorvt.context.msg.DelayMsg;
import org.magicghostvu.actorvt.context.msg.SystemMsg;
import org.magicghostvu.actorvt.context.timer.SingleTimerData;
import org.magicghostvu.actorvt.context.timer.TimerData;
import org.magicghostvu.actorvt.context.timer.TimerManData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public final class GeneralActorContext<Protocol> extends ActorContext {

    public final ActorSystem actorSystem;
    private final ActorContext parent;

    private final Logger logger = LoggerFactory.getLogger("actor-context");
    private final ReentrantLock lock = new ReentrantLock(true);

    public volatile boolean active = true;

    private final String path;

    @Nullable
    private AbstractBehavior<Protocol> state;

    final TimerManData<Protocol> timerManData;

    @Nullable
    private ActorRef<Protocol> self;

    @Nullable
    private Future<?> job;

    public final BlockingQueue<Object> messageQueue;

    GeneralActorContext(ActorSystem actorSystem, ActorContext parent, int queueCapacity, String path) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queue capacity must be greater than 0");
        }
        this.actorSystem = actorSystem;
        this.parent = parent;
        this.path = path;
        this.messageQueue = queueCapacity > 1024
                ? new LinkedBlockingQueue<>()
                : new ArrayBlockingQueue<>(queueCapacity);
        this.timerManData = new TimerManData<>(this);
    }

    @Override
    public String getPath() {
        return path;
    }

    public TimerManData<Protocol> getTimer() {
        return timerManData;
    }

    @Override
    public <P> ActorRef<P> spawn(String childName, int queueCapacity, ActorVTSupplier<Behavior<P>> behaviorFactory) {
        lock.lock();
        try {
            ActorRef<Protocol> s = requireSelf();
            if (!active) {
                logger.warn("context {} not active", s.path);
                throw new IllegalArgumentException("context " + s.path + " not active");
            }
            var childContext = new GeneralActorContext<P>(actorSystem, this, queueCapacity, path + "/" + childName);
            var childRef = new ActorRef<>(childName, childContext);
            childContext.start(childRef, behaviorFactory);
            refToChild.put(childRef, childContext);
            return childRef;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stopChild(ActorRef<?> actorRef) {
        GeneralActorContext<?> childContext = refToChild.get(actorRef);
        if (childContext != null) {
            childContext.stop(TypeStop.FROM_PARENT);
            refToChild.remove(actorRef);
        } else {
            logger.debug("maybe child {} stopped before", actorRef.path);
        }
    }

    void start(ActorRef<Protocol> self, ActorVTSupplier<Behavior<Protocol>> factory) {
        this.self = self;

        Behavior<Protocol> initState = factory.get();
        if (!(initState instanceof AbstractBehavior)) {
            initState = unwrapBehavior(initState);
        }
        if (!(initState instanceof AbstractBehavior)) {
            throw new IllegalArgumentException("init state must be an instance of AbstractBehavior");
        }
        state = (AbstractBehavior<Protocol>) initState;

        job = actorSystem.threadPool.submit((Callable<Void>) () -> {
            while (active) {
                Object msg;
                try {
                    msg = messageQueue.take();
                } catch (InterruptedException e) {
                    active = false;
                    break;
                }
                try {
                    onMsgCome(msg);
                } catch (Exception e) {
                    stop(TypeStop.SELF);
                    logger.error("err happened when actor {} process msg", requireSelf().path, e);
                }
            }
            return null;
        });
    }

    private void onMsgCome(Object msg) {
        Protocol msgWillProcess;

        if (msg instanceof SystemMsg) {
            if (msg instanceof DelayMsg<?>(Object msg1, Object key, int generation)) {
                TimerData current = timerManData.findTimerData(key);
                if (current == null) {
                    logger.debug("this key {} had been cancelled before", key);
                    msgWillProcess = null;
                } else if (current.generation != generation) {
                    logger.debug("this key {} is overridden by another timer", key);
                    msgWillProcess = null;
                } else {
                    if (current instanceof SingleTimerData) {
                        timerManData.removeKey(key, false);
                    }
                    @SuppressWarnings("unchecked")
                    Protocol typedMsg = (Protocol) msg1;
                    msgWillProcess = typedMsg;
                }
            } else {
                msgWillProcess = null;
            }
        } else {
            @SuppressWarnings("unchecked")
            Protocol typedMsg = (Protocol) msg;
            msgWillProcess = typedMsg;
        }

        if (msgWillProcess == null) return;

        AbstractBehavior<Protocol> currentState = requireState();
        Behavior<Protocol> newState = currentState.onReceive(msgWillProcess);

        if (newState == Behaviors.same()) return;
        if (newState == Behaviors.stopped()) {
            stop(TypeStop.SELF);
            return;
        }

        if (!(newState instanceof AbstractBehavior)) {
            newState = unwrapBehavior(newState);
        }

        if (newState == Behaviors.stopped()) {
            stop(TypeStop.SELF);
            return;
        }
        if (newState == Behaviors.same()) return;

        if (newState instanceof AbstractBehavior) {
            state = (AbstractBehavior<Protocol>) newState;
        } else {
            logger.error("internal err, review code");
            throw new IllegalStateException("internal err, review code");
        }
    }

    private Behavior<Protocol> unwrapBehavior(Behavior<Protocol> behavior) {
        Behavior<Protocol> tmp = behavior;
        while (tmp instanceof TimerBehavior || tmp instanceof SetUpBehavior) {
            if (tmp instanceof TimerBehavior<Protocol> tb) {
                tmp = tb.timerFunc.apply(timerManData);
            } else {
                SetUpBehavior<Protocol> sb = (SetUpBehavior<Protocol>) tmp;
                tmp = sb.factory.apply(this);
            }
        }
        return tmp;
    }

    void stop(TypeStop typeStop) {
        lock.lock();
        try {
            if (!active) return;

            if (!refToChild.isEmpty()) {
                var futures = new ArrayList<Future<?>>();
                for (GeneralActorContext<?> child : refToChild.values()) {
                    futures.add(actorSystem.threadPool.submit((Callable<Void>) () -> {
                        child.stop(TypeStop.FROM_PARENT);
                        return null;
                    }));
                }
                for (Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (ExecutionException | InterruptedException e) {
                        logger.debug("exception while waiting for child to stop", e);
                    }
                }
                refToChild.clear();
            }

            active = false;
            timerManData.cancelAll();

            ActorRef<Protocol> selfRef = requireSelf();
            selfRef.context = null;

            if (typeStop == TypeStop.FROM_PARENT) {
                final Future<?> j = job;
                if (j != null) j.cancel(true);
            } else {
                GeneralActorContext<?> removed = parent.refToChild.get(selfRef);
                if (removed == null) {
                    logger.debug("child {} of {} may have been removed before", selfRef.name, parent.getPath());
                } else {
                    parent.refToChild.remove(selfRef);
                    logger.debug("removed child {} of {}", selfRef.name, parent.getPath());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public ActorRef<Protocol> self() {
        return requireSelf();
    }

    private ActorRef<Protocol> requireSelf() {
        ActorRef<Protocol> s = self;
        if (s == null) throw new IllegalStateException("actor not started");
        return s;
    }

    private AbstractBehavior<Protocol> requireState() {
        AbstractBehavior<Protocol> s = state;
        if (s == null) throw new IllegalStateException("actor state not initialized");
        return s;
    }
}
