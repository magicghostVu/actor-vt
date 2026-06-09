package org.magicghostvu.actorvt.context;

import org.magicghostvu.actorvt.ActorRef;
import org.magicghostvu.actorvt.behavior.Behavior;

import java.util.concurrent.ConcurrentHashMap;

public sealed abstract class ActorContext permits ActorSystem, GeneralActorContext {

    public abstract String getPath();

    final ConcurrentHashMap<ActorRef<?>, GeneralActorContext<?>> refToChild = new ConcurrentHashMap<>();

    public abstract <Protocol> ActorRef<Protocol> spawn(
            String childName,
            int queueCapacity,
            ActorVTSupplier<Behavior<Protocol>> behaviorFactory
    );

    public abstract void stopChild(ActorRef<?> actorRef);
}
