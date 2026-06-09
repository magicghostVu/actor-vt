package org.magicghostvu.actorvt.context;

import org.magicghostvu.actorvt.ActorRef;
import org.magicghostvu.actorvt.behavior.Behavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class ActorSystem extends ActorContext {

    private final String rootPath;
    public final ExecutorService threadPool;
    private final boolean ownThreadPool;
    private boolean active = true;
    private final ReentrantLock lock = new ReentrantLock();
    private final Logger logger = LoggerFactory.getLogger("actor-system");

    public ActorSystem() {
        this("/");
    }

    public ActorSystem(String rootPath) {
        this.rootPath = rootPath;
        this.threadPool = Executors.newVirtualThreadPerTaskExecutor();
        this.ownThreadPool = true;
    }

    public ActorSystem(ExecutorService threadPool, String rootPath) {
        this.rootPath = rootPath;
        this.threadPool = threadPool;
        this.ownThreadPool = false;
    }

    @Override
    public String getPath() {
        return rootPath;
    }

    @Override
    public <Protocol> ActorRef<Protocol> spawn(
            String childName,
            int queueCapacity,
            ActorVTSupplier<Behavior<Protocol>> behaviorFactory
    ) {
        lock.lock();
        try {
            if (!active) {
                throw new IllegalArgumentException("actor system destroyed");
            }
            var newContext = new GeneralActorContext<Protocol>(this, this, queueCapacity, rootPath + "/" + childName);
            var actorRef = new ActorRef<>(childName, newContext);
            newContext.start(actorRef, behaviorFactory);
            refToChild.put(actorRef, newContext);
            return actorRef;
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
            logger.debug("maybe child {} of root stopped before", actorRef.path);
        }
    }

    public void destroy() {
        lock.lock();
        try {
            if (!active) {
                logger.warn("actor system already destroyed");
                return;
            }
            // Stop all children first — each stop() cancels (interrupts) the actor thread.
            // Without this, threadPool.shutdown() would block forever waiting for actor
            // message-loop threads that are blocking on messageQueue.take().
            for (GeneralActorContext<?> child : refToChild.values()) {
                child.stop(TypeStop.FROM_PARENT);
            }
            refToChild.clear();
            active = false;
            if (ownThreadPool) {
                threadPool.shutdownNow();
                try {
                    if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("thread pool did not terminate within 5 s after destroy");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
