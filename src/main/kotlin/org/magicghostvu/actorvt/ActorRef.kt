package org.magicghostvu.actorvt


import org.magicghostvu.actorvt.context.GeneralActorContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// nên cho context thành nullable để detach khỏi actor ref ngay sau khi actor bị stop??
// ở cài đặt hiện tại nếu user có một actor đã bị xoá nhưng mà vẫn giữ ref thì data của actor đó vẫn còn trong memory
class ActorRef<in Protocol> internal constructor(
    val name: String,
    internal var context: GeneralActorContext<@UnsafeVariance Protocol>?,
) {

    lateinit var path: String

    init {
        val c = context
        if (c != null) {
            path = c.path
        }
    }

    // end user will use this
    fun tell(message: Protocol) {
        val c = context
        if (c != null && c.active) {
            c.messageQueue.put(message as Any)
        } else {
            logger.warn("actor {} not active so can not send msg {}", path, message)
        }
    }

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("actor-vt")
    }
}