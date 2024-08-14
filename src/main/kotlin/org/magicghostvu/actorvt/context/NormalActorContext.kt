package org.magicghostvu.actorvt.context

import org.magicghostvu.actorvt.ActorRef
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


// context này sẽ được tạo ra khi nào?
// khi spawn thì lưu ở đâu??
// ở trong cha và ở trong ActorRef
// mỗi một child này đại diện cho một actor
class NormalActorContext<Protocol> : ActorContext() {
    // truyền vào behavior và trả ra ActorRef????




    private val logger: Logger = LoggerFactory.getLogger("actor-context")

    private val parent: ActorContext = TODO()

    private val lock = ReentrantLock(true)

    internal var active = true

    override fun spawn(childName: String) {
        // lock và check active mới được tạo
        TODO("Not yet implemented")
    }


    internal val actorSystem: ActorSystem = TODO()

    internal val messageQueue: BlockingQueue<Any> = TODO()


    // đại diện cho cái thread mà chạy logic cho cái msg này
    // nó sẽ loop queue để xử lý msg
    private lateinit var job: Future<*>


    // xem xét lưu lại thread id để ngăn chặn việc spawn hoặc kill child ở sai thread??


    init {
        job = actorSystem.threadPool.submit {
            // gán thread id
            while (active) {
                val msg = try {
                    messageQueue.take()
                } catch (e: InterruptedException) {
                    // job bị hủy từ phía parent
                    stop(TypeStop.FROM_PARENT)
                    active = false
                    break
                }
                // todo: do logic with msg
                try {
                    // thực hiện các logic nhận msg ở đây
                } catch (e: Exception) {
                    // stop các con ở đây
                    // ghi log
                    stop(TypeStop.SELF)
                    logger.error("err happened when actor ${self().path} process msg", e)
                    throw e
                }
            }
        }
    }

    //không cho phép stop gracefully
    // nếu user muốn stop gracefully, hãy tự cài đặt

    fun stopChild(childRef: ActorRef<*>) {
        val childContext = nameToChild[childRef.name]
        if (childContext != null) {
            if (childContext === childRef.context) {
                //todo: stop child
                childContext.stop(TypeStop.FROM_PARENT)
            } else {
                logger.error("actor {} not child of {}", childRef.path, this.self().path)
            }
        } else {
            logger.debug("maybe child {} stopped before", childRef.path)
        }
    }

    // phải lock
    // có thể được gọi do 2 trường hợp
    // 1. tự gọi trong trường hợp gặp stop() signal hoặc crash khi xử lý msg/un-wrap behavior
    // 2. parent gọi stop các con
    internal fun stop(typeStop: TypeStop) {
        lock.withLock {
            if (active) {
                val structuredTaskScope = StructuredTaskScope.ShutdownOnFailure()
                structuredTaskScope.use {
                    for (child in nameToChild.values) {
                        val j = it.fork {
                            child.stop(TypeStop.FROM_PARENT)
                        }
                    }
                    it.join()
                }
                nameToChild.clear()
                active = false
                job.cancel(true)
                when (typeStop) {
                    TypeStop.FROM_PARENT -> {}
                    TypeStop.SELF -> {
                        //todo: thông báo cho parent là mình đã bị kill
                        // hoặc trực tiếp remove mình khỏi map của parent??
                        val self = self()
                        val removed = parent.nameToChild.remove(self.name)
                        if (removed == null) {
                            logger.warn("child {} of {} may not be removed", self.name, self.path)
                        }
                    }
                }
            }
        }
    }


    //phải có fsm ở đây,
    // và một timer data

    fun self(): ActorRef<Protocol> {
        TODO()
    }
}