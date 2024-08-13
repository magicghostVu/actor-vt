package org.magicghostvu.actorvt


import org.magicghostvu.actorvt.context.NormalActorContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ActorRef<in Protocol>() {

    private val logger: Logger = LoggerFactory.getLogger("actor-ref")

    // full path
    internal lateinit var path: String;

    //cần thiết để lấy được context từ name khi check stop child actor
    internal lateinit var name: String

    internal lateinit var context: NormalActorContext<@UnsafeVariance Protocol>;

    private val active: Boolean
        get() {
            return context.active
        }

    // chỉ để gửi vào nên sẽ safe
    //internal lateinit var queue: BlockingQueue<@UnsafeVariance Protocol>


    fun tell(message: Protocol & Any) {
        if (active) {
            context.messageQueue.put(message)
        } else {
            logger.warn("actor {} not active so can not send msg {}", path, message)
        }
    }
}