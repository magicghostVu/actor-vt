package run_test

import org.magicghostvu.actorvt.context.ActorSystem
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

fun main() {
    val vThread = Thread
        .ofVirtual()
        .name("vt-main")
        .start {
            _main()
        }
    vThread.join()
}

fun _main() {
    val actorSystem = ActorSystem()
    val actorRef = actorSystem.spawn(
        "my-actor-1",
        128,
    ) {
        MyActorVT.setup()
    }


    Thread.sleep(200_000)

}

fun testInterrupt() {
    val logger = LoggerFactory.getLogger("common")
    val pool = Executors.newVirtualThreadPerTaskExecutor()
    pool.use {
        val f1 = pool.submit {
            try {
                Thread.sleep(1200)
                logger.info("never printed")
            } catch (e: InterruptedException) {
                logger.warn("interrupted", e)
                throw e
            }
        }

        pool.submit {
            Thread.sleep(1500)
            f1.cancel(true)
        }

    }
}