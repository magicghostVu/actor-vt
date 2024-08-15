package org.magicghostvu.actorvt.behavior

import org.magicghostvu.actorvt.context.GeneralActorContext
import org.magicghostvu.actorvt.context.timer.TimerManData

object Behaviors {
    fun <T> same(): Behavior<T> {
        return Same as Behavior<T>
    }


    fun <T> stopped(): Behavior<T> {
        return Stopped as Behavior<T>
    }


    public fun <T> setUp(factory: (GeneralActorContext<T>) -> Behavior<T>): Behavior<T> {
        return SetUpBehavior(factory)
    }


    public fun <T> withTimer(doWithTimer: (TimerManData<T>) -> Behavior<T>): Behavior<T> {
        return TimerBehavior(doWithTimer)
    }


}

internal data object Same : Behavior<Any>()
internal data object Stopped : Behavior<Any>()