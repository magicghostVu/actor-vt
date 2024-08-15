package org.magicghostvu.actorvt


import org.magicghostvu.actorvt.context.GeneralActorContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ActorRef<in Protocol> internal constructor(
    val context: GeneralActorContext<@UnsafeVariance Protocol>,
    val name: String
) {


    /*// full path
    internal lateinit var path: String;

    //cần thiết để lấy được context từ name khi check stop child actor
    internal lateinit var name: String

    internal lateinit var context: GeneralActorContext<@UnsafeVariance Protocol>;*/

    val path: String
        get() = context.path

    private val active: Boolean
        get() {
            return context.active
        }

    // end user will use this
    fun tell(message: Protocol) {
        if (active) {
            context.messageQueue.put(message as Any)
        } else {
            logger.warn("actor {} not active so can not send msg {}", path, message)
        }
    }

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("actor-vt")
    }
}