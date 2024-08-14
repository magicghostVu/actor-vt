package org.magicghostvu.actorvt.behavior

import org.magicghostvu.actorvt.context.NormalActorContext

sealed class Behavior<in Protocol> {

}

// setup behavior
// timer behavior

//abstract behavior
// end-dev will implement it
abstract class AbstractBehavior<Protocol>(val context: NormalActorContext<Protocol>) : Behavior<Protocol>() {
    abstract fun onReceive(message: Protocol)
}