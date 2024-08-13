package org.magicghostvu.actorvt.context

import org.magicghostvu.actorvt.ActorRef
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


    // thông báo khi bản thân bị kill tự nguyện
    // nếu bị parent kill thì không cần
    private val parent: ActorContext = TODO()

    private val lock = ReentrantLock(true)

    internal var active = true

    override fun spawn(childName: String) {
        // lock và check active mới được tạo
        TODO("Not yet implemented")
    }


    private val actorSystem: ActorSystem = TODO()

    private val messageQueue: BlockingQueue<Any> = TODO()


    // đại diện cho cái thread mà chạy logic cho cái msg này
    // nó sẽ loop queue để xử lý msg
    private lateinit var job: Future<*>


    init {
        job = actorSystem.threadPool.submit {
            while (active) {
                val msg = messageQueue.take()
                // todo: do logic with msg
            }
        }
    }

    // phải lock
    // có thể được gọi do 2 trường hợp
    // 1. tự gọi trong trường hợp gặp stop() signal
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
                self().active = false
                job.cancel(true)
                when (typeStop) {
                    TypeStop.FROM_PARENT -> {}
                    TypeStop.SELF -> {
                        //todo: thông báo cho parent là mình đã bị kill
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