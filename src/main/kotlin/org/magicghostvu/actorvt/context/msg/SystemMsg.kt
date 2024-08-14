package org.magicghostvu.actorvt.context.msg


internal sealed class SystemMsg {
}

internal class DelayMsg<T>() : SystemMsg()


// gửi cho child để children stop
