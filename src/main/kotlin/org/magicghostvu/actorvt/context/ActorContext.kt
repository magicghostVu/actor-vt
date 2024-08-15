package org.magicghostvu.actorvt.context

import org.magicghostvu.actorvt.ActorRef
import org.magicghostvu.actorvt.behavior.Behavior
import java.util.concurrent.ConcurrentHashMap

sealed class ActorContext {


    internal abstract val path: String;

    // name -> child
    internal val refToChild: MutableMap<ActorRef<*>, GeneralActorContext<*>> = ConcurrentHashMap()


    // expect func này chỉ được gọi trong actor thread
    // todo: re-design this method signature
    abstract fun <Protocol> spawn(
        childName: String,
        queueCapacity: Int,
        behaviorFactory: () -> Behavior<Protocol>
    ): ActorRef<Protocol>;

    abstract fun stopChild(actorRef: ActorRef<*>);
}