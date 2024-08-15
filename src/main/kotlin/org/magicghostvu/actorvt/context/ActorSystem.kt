package org.magicghostvu.actorvt.context

import org.magicghostvu.actorvt.ActorRef
import org.magicghostvu.actorvt.behavior.Behavior
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ActorSystem(private val rootPath: String = "/") : ActorContext() {

    private val lock = ReentrantLock()

    private val logger: Logger = LoggerFactory.getLogger("actor-system")

    // mỗi một actor con sẽ chạy trên một virtual thread
    internal val threadPool = Executors.newVirtualThreadPerTaskExecutor();

    override val path: String
        get() = rootPath

    // check name exist
    override fun <Protocol> spawn(
        childName: String,
        queueCapacity: Int,
        behaviorFactory: () -> Behavior<Protocol>
    ): ActorRef<Protocol> {
        return lock.withLock {
            if (nameToChild.containsKey(childName)) {
                throw IllegalArgumentException("$childName is already in use")
            }
            // tạo context mới
            val newContext: NormalActorContext<Protocol> = NormalActorContext(
                this,
                this,
                queueCapacity
            )
            newContext.path = "${this.rootPath}/$childName"
            val actorRef = ActorRef<Protocol>()
            actorRef.context = newContext
            actorRef.name = childName
            actorRef.path = newContext.path
            newContext.self = actorRef
            newContext.start(factory = behaviorFactory)
            nameToChild[childName] = newContext
            actorRef
        }
    }

    override fun stopChild(actorRef: ActorRef<*>) {
        val childContext = nameToChild[actorRef.name]
        if (childContext != null) {
            if (childContext === actorRef.context) {
                //todo: stop child
                childContext.stop(TypeStop.FROM_PARENT)
                nameToChild.remove(actorRef.name)
            } else {
                logger.error("actor {} not child of root", actorRef.path)
            }
        } else {
            logger.debug("maybe child {} of root stopped before", actorRef.path)
        }
    }

    fun destroy() = threadPool.close()
}