package org.magicghostvu.actorvt.context

import java.util.concurrent.ConcurrentHashMap

sealed class ActorContext {

    // name -> child
    internal val nameToChild: MutableMap<String, NormalActorContext<*>> = ConcurrentHashMap()


    // expect func này chỉ được gọi trong actor thread
    // todo: re-design this method signature
    abstract fun spawn(childName: String);
}