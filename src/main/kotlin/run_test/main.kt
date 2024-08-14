package run_test

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.StructuredTaskScope

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
    val logger = LoggerFactory.getLogger("common")
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
    }
}