package run_test

import org.magicghostvu.actorvt.context.ActorSystem

fun main() {
    System.setProperty("log4j.configurationFile", "./log4j2.xml")
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