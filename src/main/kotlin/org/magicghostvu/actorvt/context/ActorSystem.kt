package org.magicghostvu.actorvt.context

import org.magicghostvu.actorvt.ActorRef
import org.magicghostvu.actorvt.behavior.Behavior
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ActorSystem(private val rootPath: String = "/") : ActorContext() {

    private var active: Boolean = true

    private val lock = ReentrantLock()

    private val logger: Logger = LoggerFactory.getLogger("actor-system")

    // mỗi một actor con sẽ chạy trên một virtual thread
    internal val threadPool = Executors.newVirtualThreadPerTaskExecutor();

    override val path: String
        get() = rootPath

    override fun <Protocol> spawn(
        childName: String,
        queueCapacity: Int,
        behaviorFactory: () -> Behavior<Protocol>
    ): ActorRef<Protocol> {
        // tạo context mới
        return lock.withLock {
            if (!active) {
                throw IllegalArgumentException("actor system destroyed")
            }
            val newContext: GeneralActorContext<Protocol> = GeneralActorContext(
                this,
                this,
                queueCapacity
            )
            newContext.path = "${this.rootPath}/$childName"
            val actorRef = ActorRef(newContext, childName)
            newContext.self = actorRef
            newContext.start(factory = behaviorFactory)
            refToChild[actorRef] = newContext
            actorRef
        }
    }

    override fun stopChild(actorRef: ActorRef<*>) {
        val childContext = refToChild[actorRef]
        if (childContext != null) {
            childContext.stop(TypeStop.FROM_PARENT)
            refToChild.remove(actorRef)
        } else {
            logger.debug("maybe child {} of root stopped before", actorRef.path)
        }
    }

    fun destroy() {
        lock.withLock {
            if (active) {
                threadPool.close()
                active = false
            } else {
                logger.warn("actor system destroyed before")
            }
        }
    }
}