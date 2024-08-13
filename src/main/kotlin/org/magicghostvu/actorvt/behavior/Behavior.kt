package org.magicghostvu.actorvt.behavior

sealed class Behavior<in Protocol> {

}

// setup behavior
// timer behavior

//abstract behavior
// end-dev will implement it
abstract class AbstractBehavior<Protocol> : Behavior<Protocol>() {
    abstract fun onReceive(message: Protocol)
}