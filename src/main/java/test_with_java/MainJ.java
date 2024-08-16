package test_with_java;

import org.magicghostvu.actorvt.context.ActorSystem;
import org.slf4j.LoggerFactory;

public class MainJ {
    public static void main(String[] args) throws InterruptedException {
        System.setProperty("log4j.configurationFile", "./log4j2.xml");
        var t = Thread
                .ofVirtual()
                .name("_main")
                .start(() -> {
                    try {
                        _main(args);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
        t.join();
        Thread.sleep(2000);
    }

    public static void _main(String[] args) throws InterruptedException {
        var logger = LoggerFactory.getLogger("actor-vt");
        logger.info("start");
        var actorSystem = new ActorSystem("p");
        // java-side use
        var actorRef = actorSystem.spawn(
                "child1",
                100,
                MyActor::setup
        );
        actorRef.tell(new ChildMsg1());
        Thread.sleep(1200);
        actorSystem.stopChild(actorRef);
        /*var ref2 = actorSystem.spawn(
                "child1",
                100,
                MyActor::setup
        );*/
        //actorRef = null;
        Thread.sleep(20_000);
        logger.info("done");
    }
}
