package run_test

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
    testInterrupt()
    /*val logger = LoggerFactory.getLogger("common")
    val c = StructuredTaskScope.ShutdownOnFailure()
    val res = c.use {
        val j1 = it.fork {
            Thread.sleep(1500)
            logger.info("thread id {}", Thread.currentThread().threadId())
            1
        }

        val j2 = it.fork {
            Thread.sleep(1000)
            logger.info("thread id {}", Thread.currentThread().threadId())
            2
        }
        it.joinUntil(Instant.ofEpochMilli(System.currentTimeMillis() + 1600))
        val j3 = j1.get() + j2.get()
        j3
    }*/
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