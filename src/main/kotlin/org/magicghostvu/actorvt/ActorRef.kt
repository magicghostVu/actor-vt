package org.magicghostvu.actorvt

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue

class ActorRef<in Protocol>() {

    private val logger: Logger = LoggerFactory.getLogger("actor-ref")
    internal lateinit var path: String;

    internal var active: Boolean = true

    // chỉ để gửi vào nên sẽ safe
    internal lateinit var queue: BlockingQueue<@UnsafeVariance Protocol>



    fun tell(message: Protocol & Any) {
        if (active) {
            queue.put(message)
        } else {
            logger.warn("actor {} not active so can not send msg {}", path, message)
        }
    }
}