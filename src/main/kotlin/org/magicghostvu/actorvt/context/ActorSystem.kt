package org.magicghostvu.actorvt.context

import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ActorSystem(val rootPath: String) : ActorContext() {

    private val lock = ReentrantLock()

    // mỗi một actor con sẽ chạy trên một virtual thread
    internal val threadPool = Executors.newVirtualThreadPerTaskExecutor();



    // check name exist
    override fun spawn(childName: String) {
        lock.withLock {

            TODO()
        }
    }



    fun destroy() = threadPool.close()
}