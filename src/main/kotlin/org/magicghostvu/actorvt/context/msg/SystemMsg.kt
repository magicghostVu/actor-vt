package org.magicghostvu.actorvt.context.msg

// maybe some other class to impl some other mechanic
internal sealed class SystemMsg {
}

internal data class DelayMsg<T>(val msg: T, val key: Any, val generation: Int) : SystemMsg()

