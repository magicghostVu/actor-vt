package org.magicghostvu.actorvt;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magicghostvu.actorvt.behavior.AbstractBehavior;
import org.magicghostvu.actorvt.behavior.Behavior;
import org.magicghostvu.actorvt.behavior.Behaviors;
import org.magicghostvu.actorvt.context.ActorSystem;
import org.magicghostvu.actorvt.context.GeneralActorContext;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ActorBasicTest {

    ActorSystem system;

    @BeforeEach
    void setUp() {
        system = new ActorSystem("test");
    }

    @AfterEach
    void tearDown() {
        system.destroy();

    }

    // ---- counter actor ----

    sealed interface CountMsg permits Increment, GetCount {}
    record Increment() implements CountMsg {}
    record GetCount(CompletableFuture<Integer> reply) implements CountMsg {}

    static class CounterActor extends AbstractBehavior<CountMsg> {
        private int count = 0;

        CounterActor(GeneralActorContext<CountMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<CountMsg> onReceive(CountMsg msg) {
            return switch (msg) {
                case Increment ignored -> {
                    count++;
                    yield Behaviors.same();
                }
                case GetCount g -> {
                    g.reply().complete(count);
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void actorReceivesMessagesInOrder() throws Exception {
        var ref = system.spawn("counter", 64, () -> Behaviors.setUp(CounterActor::new));
        for (int i = 0; i < 5; i++) ref.tell(new Increment());
        var reply = new CompletableFuture<Integer>();
        ref.tell(new GetCount(reply));
        assertEquals(5, reply.get(2, TimeUnit.SECONDS));
    }

    // ---- stoppable actor ----

    sealed interface StopMsg permits Work, Stop {}
    record Work() implements StopMsg {}
    record Stop(CompletableFuture<Void> done) implements StopMsg {}

    static class StoppableActor extends AbstractBehavior<StopMsg> {
        StoppableActor(GeneralActorContext<StopMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<StopMsg> onReceive(StopMsg msg) {
            return switch (msg) {
                case Work ignored -> Behaviors.same();
                case Stop s -> {
                    s.done().complete(null);
                    yield Behaviors.stopped();
                }
            };
        }
    }

    static void awaitStopped(ActorRef<?> ref) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (ref.context != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(2);
        }
        assertNull(ref.context, "actor did not stop in time");
    }

    @Test
    void actorStopsSelf() throws Exception {
        var ref = system.spawn("stoppable", 32, () -> Behaviors.setUp(StoppableActor::new));
        ref.tell(new Work());
        var done = new CompletableFuture<Void>();
        ref.tell(new Stop(done));
        done.get(2, TimeUnit.SECONDS);
        awaitStopped(ref);
        assertThrows(IllegalArgumentException.class, () -> ref.tell(new Work()));
    }

    @Test
    void parentStopsChild() throws Exception {
        var ref = system.spawn("child", 32, () -> Behaviors.setUp(StoppableActor::new));
        ref.tell(new Work());
        system.stopChild(ref); // synchronous — context is null upon return
        assertThrows(IllegalArgumentException.class, () -> ref.tell(new Work()));
    }

    @Test
    void tellOnStoppedActorThrows() throws Exception {
        var ref = system.spawn("dying", 32, () -> Behaviors.setUp(StoppableActor::new));
        var done = new CompletableFuture<Void>();
        ref.tell(new Stop(done));
        done.get(2, TimeUnit.SECONDS);
        awaitStopped(ref);
        assertThrows(IllegalArgumentException.class, () -> ref.tell(new Work()));
    }

    // ---- behavior state transition ----

    sealed interface ToggleMsg permits Toggle, QueryState {}
    record Toggle() implements ToggleMsg {}
    record QueryState(CompletableFuture<String> reply) implements ToggleMsg {}

    static class OnBehavior extends AbstractBehavior<ToggleMsg> {
        OnBehavior(GeneralActorContext<ToggleMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<ToggleMsg> onReceive(ToggleMsg msg) {
            return switch (msg) {
                case Toggle ignored -> new OffBehavior(context);
                case QueryState q -> {
                    q.reply().complete("on");
                    yield Behaviors.same();
                }
            };
        }
    }

    static class OffBehavior extends AbstractBehavior<ToggleMsg> {
        OffBehavior(GeneralActorContext<ToggleMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<ToggleMsg> onReceive(ToggleMsg msg) {
            return switch (msg) {
                case Toggle ignored -> new OnBehavior(context);
                case QueryState q -> {
                    q.reply().complete("off");
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void behaviorTransitionsOnMessage() throws Exception {
        var ref = system.spawn("toggle", 32, () -> Behaviors.setUp(OnBehavior::new));

        var q1 = new CompletableFuture<String>();
        ref.tell(new QueryState(q1));
        assertEquals("on", q1.get(2, TimeUnit.SECONDS));

        ref.tell(new Toggle());

        var q2 = new CompletableFuture<String>();
        ref.tell(new QueryState(q2));
        assertEquals("off", q2.get(2, TimeUnit.SECONDS));

        ref.tell(new Toggle());

        var q3 = new CompletableFuture<String>();
        ref.tell(new QueryState(q3));
        assertEquals("on", q3.get(2, TimeUnit.SECONDS));
    }

    // ---- crash isolation ----

    sealed interface CrashMsg permits Crash, Ping {}
    record Crash() implements CrashMsg {}
    record Ping(CompletableFuture<String> reply) implements CrashMsg {}

    static class CrashableActor extends AbstractBehavior<CrashMsg> {
        CrashableActor(GeneralActorContext<CrashMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<CrashMsg> onReceive(CrashMsg msg) {
            return switch (msg) {
                case Crash ignored -> throw new RuntimeException("intentional crash");
                case Ping p -> {
                    p.reply().complete("pong");
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void actorCrashStopsActor() throws Exception {
        var ref = system.spawn("crashable", 32, () -> Behaviors.<CrashMsg>setUp(CrashableActor::new));
        ref.tell(new Crash());
        awaitStopped(ref);
        assertThrows(IllegalArgumentException.class,
                () -> ref.tell(new Ping(new CompletableFuture<>())));
    }

    // ---- parent-child hierarchy ----

    sealed interface ParentMsg permits SpawnChild, StopChildMsg, PingParent {}
    record SpawnChild(CompletableFuture<String> childReply) implements ParentMsg {}
    record StopChildMsg() implements ParentMsg {}
    record PingParent(CompletableFuture<String> reply) implements ParentMsg {}

    static class ParentActor extends AbstractBehavior<ParentMsg> {
        @Nullable
        ActorRef<CountMsg> child;

        ParentActor(GeneralActorContext<ParentMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<ParentMsg> onReceive(ParentMsg msg) {
            return switch (msg) {
                case SpawnChild s -> {
                    child = context.spawn("counter-child", 32, () -> Behaviors.setUp(CounterActor::new));
                    var reply = new CompletableFuture<Integer>();
                    child.tell(new GetCount(reply));
                    reply.thenAccept(c -> s.childReply().complete("child-count:" + c));
                    yield Behaviors.same();
                }
                case StopChildMsg ignored -> {
                    var c = child;
                    if (c != null) {
                        context.stopChild(c);
                        child = null;
                    }
                    yield Behaviors.same();
                }
                case PingParent p -> {
                    p.reply().complete("pong");
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void parentSpawnsAndStopsChild() throws Exception {
        var parent = system.spawn("parent", 32, () -> Behaviors.setUp(ParentActor::new));
        var childReply = new CompletableFuture<String>();
        parent.tell(new SpawnChild(childReply));
        assertEquals("child-count:0", childReply.get(2, TimeUnit.SECONDS));

        parent.tell(new StopChildMsg());

        var pong = new CompletableFuture<String>();
        parent.tell(new PingParent(pong));
        assertEquals("pong", pong.get(2, TimeUnit.SECONDS));
    }

    // ---- concurrent message delivery ----

    @Test
    void concurrentTellsAreAllReceived() throws Exception {
        var logger = LoggerFactory.getLogger(getClass());
        var ref = system.spawn("counter2", 1024, () -> Behaviors.setUp(CounterActor::new));
        int n = 500;
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < n; i++) {
            threads.add(
                    Thread.ofVirtual().start(() -> ref.tell(new Increment()))
            );
        }
        for (var t : threads) t.join();

        var reply = new CompletableFuture<Integer>();
        ref.tell(new GetCount(reply));
        var ss = reply.get(2, TimeUnit.SECONDS);
        logger.info("ss is {}", ss);
        assertEquals(n, ss);
    }

}
