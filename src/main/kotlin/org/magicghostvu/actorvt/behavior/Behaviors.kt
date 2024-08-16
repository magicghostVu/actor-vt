package org.magicghostvu.actorvt.behavior

import org.magicghostvu.actorvt.context.GeneralActorContext
import org.magicghostvu.actorvt.context.timer.TimerManData

object Behaviors {
    @JvmStatic
    fun <T> same(): Behavior<T> {
        return Same as Behavior<T>
    }


    @JvmStatic
    fun <T> stopped(): Behavior<T> {
        return Stopped as Behavior<T>
    }

    @JvmStatic
    public fun <T> setUp(factory: (GeneralActorContext<T>) -> Behavior<T>): Behavior<T> {
        return SetUpBehavior(factory)
    }


    @JvmStatic
    public fun <T> withTimer(setupTimer: (TimerManData<T>) -> Behavior<T>): Behavior<T> {
        return TimerBehavior(setupTimer)
    }


}

internal data object Same : Behavior<Any>()
internal data object Stopped : Behavior<Any>()