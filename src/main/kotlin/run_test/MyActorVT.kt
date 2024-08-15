package run_test

import org.magicghostvu.actorvt.behavior.AbstractBehavior
import org.magicghostvu.actorvt.behavior.Behavior
import org.magicghostvu.actorvt.behavior.Behaviors
import org.magicghostvu.actorvt.context.GeneralActorContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MyActorVT(context: GeneralActorContext<MyMsg>) : AbstractBehavior<MyMsg>(context) {

    private val logger: Logger = LoggerFactory.getLogger("my-actor-vt")

    override fun onReceive(message: MyMsg): Behavior<MyMsg> {
        return when (message) {
            is M1 -> {
                logger.debug("received m1")
                context.timer.cancel(KeyM3Timer)
                logger.debug("m3 timer cancelled")
                Behaviors.same()
            }

            is M2 -> {
                logger.debug("m2 received")
                Behaviors.same()
            }

            M3 -> {
                logger.debug("m3 received")
                Behaviors.same()
            }
        }
    }

    companion object {
        fun setup(): Behavior<MyMsg> {
            return Behaviors.withTimer { timer ->
                timer.startFixedRateTimer(KeyM3Timer, M3, 1000, 3000)
                Behaviors.setUp {
                    MyActorVT(it)
                }
            }
        }
    }
}

sealed class MyMsg

class M1 : MyMsg()
class M2 : MyMsg()
data object M3 : MyMsg()

data object KeyM3Timer