package test_with_java;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.magicghostvu.actorvt.behavior.AbstractBehavior;
import org.magicghostvu.actorvt.behavior.Behavior;
import org.magicghostvu.actorvt.behavior.Behaviors;
import org.magicghostvu.actorvt.context.NormalActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyActor extends AbstractBehavior<MyMessage> {
    public MyActor(@NotNull NormalActorContext<MyMessage> context) {
        super(context);
    }


    private final Logger logger = LoggerFactory.getLogger("my-actor");

    @NotNull
    @Override
    public Behavior<MyMessage> onReceive(MyMessage message) {
        logger.info("path is {}", getContext().path);

        switch (message) {
            case ChildMsg1 m -> {
                logger.info("received ChildMsg1");
                return Behaviors.INSTANCE.same();
            }
            case ChildMsg2 m -> {
                logger.info("received ChildMsg2");
                return Behaviors.INSTANCE.same();
            }
            case KillMyActor k -> {
                logger.info("received KillMyActor");
                k.repTo.complete(Unit.INSTANCE);
                return Behaviors.INSTANCE.stopped();
            }
        }
    }


    public static Behavior<MyMessage> setup() {
        return Behaviors.INSTANCE.setUp(MyActor::new);
    }
}
