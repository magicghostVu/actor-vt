package test_with_java;

import org.magicghostvu.actorvt.context.ActorSystem;
import org.slf4j.LoggerFactory;

public class MainJ {
    public static void main(String[] args) throws Exception {
        var t = Thread
                .ofVirtual()
                .name("_main")
                .start(() -> _main(args));
        t.join();
        Thread.sleep(2000);
    }

    public static void _main(String[] args) {
        var logger = LoggerFactory.getLogger("actor-vt");
        logger.info("start");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        var actorSystem = new ActorSystem("p");
        // java-side use
        var actorRef = actorSystem.spawn(
                "child1",
                100,
                MyActor::setup
        );

        actorRef.tell(new ChildMsg1());
        try {
            Thread.sleep(1200);
        } catch (Exception e) {
            logger.error("err while sleep", e);
            throw new RuntimeException(e);
        }
        actorSystem.stopChild(actorRef);
        try {
            /*msg.repTo.get();
            logger.info("actor die");*/

            actorSystem.spawn(
                    "child1",
                    100,
                    MyActor::setup
            );

            Thread.sleep(1000);
        } catch (Exception e) {
            logger.error("err while get result", e);
        }

        //actorRef.tell(new KillMyActor());
        logger.info("done");
    }
}
