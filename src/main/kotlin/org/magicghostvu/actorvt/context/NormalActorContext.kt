package org.magicghostvu.actorvt.context

import org.magicghostvu.actorvt.ActorRef
import org.magicghostvu.actorvt.behavior.*
import org.magicghostvu.actorvt.behavior.SetUpBehavior
import org.magicghostvu.actorvt.behavior.TimerBehavior
import org.magicghostvu.actorvt.context.msg.DelayMsg
import org.magicghostvu.actorvt.context.msg.SystemMsg
import org.magicghostvu.actorvt.context.timer.PeriodicTimerData
import org.magicghostvu.actorvt.context.timer.SingleTimerData
import org.magicghostvu.actorvt.context.timer.TimerManData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


// context này sẽ được tạo ra khi nào?
// khi spawn thì lưu ở đâu??
// ở trong cha và ở trong ActorRef
// mỗi một child này đại diện cho một actor
class NormalActorContext<Protocol>(
    val actorSystem: ActorSystem,
    val parent: ActorContext,
    val queueCapacity: Int
) : ActorContext() {


    private val logger: Logger = LoggerFactory.getLogger("actor-context")

    private val lock = ReentrantLock(true)

    internal var active = true

    override lateinit var path: String


    internal lateinit var state: AbstractBehavior<Protocol>

    internal val timerManData: TimerManData<Protocol>

    internal lateinit var self: ActorRef<Protocol>

    override fun <Protocol> spawn(
        childName: String,
        queueCapacity: Int,
        behaviorFactory: () -> Behavior<Protocol>
    ): ActorRef<Protocol> {
        TODO("Not yet implemented")
    }


    // sử dụng linked blocking queue hay array blocking queue??
    internal val messageQueue: BlockingQueue<Any>


    // đại diện cho cái thread/job mà chạy logic cho actor này
    // nó sẽ loop queue để xử lý msg
    private lateinit var job: Future<*>


    // xem xét lưu lại thread id để ngăn chặn việc spawn hoặc kill child ở sai thread??


    init {
        if (queueCapacity <= 0) {
            throw IllegalArgumentException("queue capacity must be greater than 0")
        }
        messageQueue = if (queueCapacity >= 1024) {
            LinkedBlockingQueue()
        } else {
            ArrayBlockingQueue(queueCapacity)
        }
        timerManData = TimerManData(this)
    }


    // nếu unwrap thành công thì mới chạy start, tránh leak thread
    internal fun start(factory: () -> Behavior<Protocol>) {


        // un-wrap behavior

        var initState = factory()
        if (initState !is AbstractBehavior<Protocol>) {
            initState = unwrapBehavior(initState)
        }
        if (initState !is AbstractBehavior<Protocol>) {
            throw IllegalArgumentException("init state must be an instance of AbstractBehavior")
        }
        state = initState


        job = actorSystem.threadPool.submit {
            // gán thread id
            while (active) {
                val msg = try {
                    messageQueue.take()
                } catch (e: InterruptedException) {
                    // job bị hủy từ phía parent
                    //stop(TypeStop.FROM_PARENT)
                    active = false
                    throw e
                }
                // todo: do logic with msg
                try {
                    // thực hiện các logic nhận msg ở đây
                    onMsgCome(msg)
                } catch (e: InterruptedException) {
                    active = false
                    //stop(TypeStop.FROM_PARENT)
                    throw e
                } catch (e: Exception) {
                    // stop các con ở đây
                    // ghi log
                    active = false
                    stop(TypeStop.SELF)
                    logger.error("err happened when actor ${self().path} process msg", e)
                    throw e
                }
            }
        }
    }


    private fun onMsgCome(msg: Any) {
        val msgWillProcess: Protocol? = when (msg) {
            is SystemMsg -> {
                when (msg) {
                    is DelayMsg<*> -> {
                        // check valid generation
                        val (m, key, generationFromMsg) = msg
                        val currentGenOfThisKey = timerManData.getCurrentGeneration(key)
                        if (currentGenOfThisKey == null) {
                            logger.debug("this key {} had been cancel before", key)
                            null
                        } else {
                            if (currentGenOfThisKey != generationFromMsg) {
                                logger.debug("this key {} is override by an other timer", key)
                                null
                            } else {
                                val timerData = timerManData.getTimerData(key)
                                when (timerData) {
                                    is SingleTimerData -> {
                                        timerManData.removeKey(key, false)
                                    }

                                    is PeriodicTimerData -> {}
                                }
                                m as Protocol
                            }
                        }
                    }
                }
            }

            else -> {
                msg as Protocol
            }
        }
        if (msgWillProcess != null) {
            var newState = state.onReceive(msgWillProcess)

            logger.debug("new state is {}", newState)

            // check stop/same... ở đây
            if (newState === Behaviors.same<Protocol>()) {
                return
            }

            if (newState === Behaviors.stopped<Protocol>()) {
                logger.debug("outside stop")
                stop(TypeStop.SELF)
                return
            }

            if (newState !is AbstractBehavior<Protocol>) {
                newState = unwrapBehavior(newState)
            }

            if (newState === Behaviors.stopped<Protocol>()) {
                stop(TypeStop.SELF)
                return
            }

            if (newState == Behaviors.same<Protocol>()) {
                return
            }

            if (newState is AbstractBehavior<Protocol>) {
                state = newState
            } else {
                logger.error("internal err, review code")
                throw IllegalStateException("internal err, review code")
            }
        }
    }

    private fun unwrapBehavior(behavior: Behavior<Protocol>): Behavior<Protocol> {
        var tmp = behavior
        while (tmp is TimerBehavior<Protocol> || tmp is SetUpBehavior<Protocol>) {
            if (tmp is TimerBehavior<Protocol>) {
                tmp = tmp.timerFunc(timerManData)
            } else if (tmp is SetUpBehavior<Protocol>) {
                tmp = tmp.factory(this)
            }
        }
        return tmp
    }

    //không cho phép stop gracefully
    // nếu user muốn stop gracefully, hãy tự cài đặt

    override fun stopChild(actorRef: ActorRef<*>) {
        val childContext = nameToChild[actorRef.name]
        if (childContext != null) {
            if (childContext === actorRef.context) {
                //todo: stop child
                childContext.stop(TypeStop.FROM_PARENT)
                nameToChild.remove(actorRef.name)
            } else {
                logger.error("actor {} not child of {}", actorRef.path, this.self().path)
            }
        } else {
            logger.debug("maybe child {} stopped before", actorRef.path)
        }
    }

    // phải lock
    // có thể được gọi do 2 trường hợp
    // 1. tự gọi trong trường hợp gặp stop() signal hoặc crash khi xử lý msg/un-wrap behavior
    // 2. parent gọi stop các con
    internal fun stop(typeStop: TypeStop) {
        //logger.debug("start stop", Exception())
        lock.withLock {
            if (active) {
                if (nameToChild.isNotEmpty()) {
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
                }
                active = false
                when (typeStop) {
                    TypeStop.FROM_PARENT -> {
                        job.cancel(true)
                    }

                    TypeStop.SELF -> {
                        //todo: thông báo cho parent là mình đã bị kill
                        // hoặc trực tiếp remove mình khỏi map của parent??
                        val self = self()
                        val removed = parent.nameToChild.remove(self.name)
                        if (removed == null) {
                            logger.debug("child {} of {} may not be removed", self.name, self.path)
                        } else {
                            logger.debug("removed child {} of {}", self.name, parent.path)
                        }
                    }
                }
            }
        }
    }


    //phải có fsm ở đây,
    // và một timer data

    fun self(): ActorRef<Protocol> = self
}