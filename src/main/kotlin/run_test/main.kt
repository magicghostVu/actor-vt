package run_test

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

    val c = StructuredTaskScope.ShutdownOnFailure()
    val res = c.use {
        val j1 = it.fork {
            Thread.sleep(1500)
            1
        }

        val j2 = it.fork {
            Thread.sleep(1000)
            2
        }
        it.joinUntil(Instant.ofEpochMilli(System.currentTimeMillis() + 1600))
        val j3 = j1.get() + j2.get()
        j3
    }
}