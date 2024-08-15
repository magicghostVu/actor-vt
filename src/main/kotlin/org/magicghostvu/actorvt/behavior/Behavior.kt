package org.magicghostvu.actorvt.behavior

import org.magicghostvu.actorvt.context.NormalActorContext
import org.magicghostvu.actorvt.context.timer.TimerManData

sealed class Behavior<in Protocol> {

}

// setup behavior
internal class SetUpBehavior<T>(val factory: (NormalActorContext<T>) -> Behavior<T>) : Behavior<T>()


// timer behavior

internal class TimerBehavior<T>(val timerFunc: (TimerManData<T>) -> Behavior<T>) : Behavior<T>()



//abstract behavior
// end-dev will implement it
abstract class AbstractBehavior<Protocol>(val context: NormalActorContext<Protocol>) : Behavior<Protocol>() {
    abstract fun onReceive(message: Protocol): Behavior<Protocol>
}