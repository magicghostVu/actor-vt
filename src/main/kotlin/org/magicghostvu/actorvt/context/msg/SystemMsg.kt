package org.magicghostvu.actorvt.context.msg

import java.util.concurrent.CompletableFuture

internal sealed class SystemMsg {
}

internal class DelayMsg<T>() : SystemMsg()


// gửi cho child để children stop
internal class StopMsg(val complete: CompletableFuture<Unit> = CompletableFuture()) : SystemMsg()
