package org.magicghostvu.actorvt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magicghostvu.actorvt.behavior.AbstractBehavior;
import org.magicghostvu.actorvt.behavior.Behavior;
import org.magicghostvu.actorvt.behavior.Behaviors;
import org.magicghostvu.actorvt.context.ActorSystem;
import org.magicghostvu.actorvt.context.GeneralActorContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TimerTest {

    ActorSystem system;

    @BeforeEach
    void setUp() {
        system = new ActorSystem("timer-test");
    }

    @AfterEach
    void tearDown() {
        system.destroy();
    }

    static final Object TICK_KEY = "tick-key";
    static final Object CANCEL_KEY = "cancel-key";

    // ---- single timer fires once — actor signals future on tick ----

    sealed interface TickMsg permits Tick, WaitForTicks {
    }

    record Tick() implements TickMsg {
    }

    // signals reply after targetCount ticks
    record WaitForTicks(int targetCount, CompletableFuture<Integer> reply) implements TickMsg {
    }


    static class TickCounterActor extends AbstractBehavior<TickMsg> {
        private int ticks = 0;
        @org.jspecify.annotations.Nullable CompletableFuture<Integer> pending;
        private int pendingTarget;

        TickCounterActor(GeneralActorContext<TickMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<TickMsg> onReceive(TickMsg msg) {
            return switch (msg) {
                case Tick ignored -> {
                    ticks++;
                    var p = pending;
                    if (p != null && ticks >= pendingTarget) {
                        p.complete(ticks);
                        pending = null;
                    }
                    yield Behaviors.same();
                }
                case WaitForTicks w -> {
                    if (ticks >= w.targetCount()) {
                        w.reply().complete(ticks);
                    } else {
                        pending = w.reply();
                        pendingTarget = w.targetCount();
                    }
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void singleTimerFiresOnce() throws Exception {
        ActorRef<TickMsg> ref = system.spawn("single", 32, () ->
                Behaviors.<TickMsg>withTimer(timer -> {
                    timer.startSingleTimer(TICK_KEY, new Tick(), 50);
                    return Behaviors.setUp(TickCounterActor::new);
                }));

        // wait until exactly 1 tick arrives (future resolves as soon as it fires)
        var reply = new CompletableFuture<Integer>();
        ref.tell(new WaitForTicks(1, reply));
        assertEquals(1, reply.get(2, TimeUnit.SECONDS));

        // wait a bit then confirm it didn't fire again
        Thread.sleep(150); // 3× timer delay — single timer must not repeat
        var recheck = new CompletableFuture<Integer>();
        ref.tell(new WaitForTicks(1, recheck));
        assertEquals(1, recheck.get(1, TimeUnit.SECONDS));
    }

    // ---- periodic timer fires at least N times ----

    @Test
    void periodicTimerFiresMultipleTimes() throws Exception {
        ActorRef<TickMsg> ref = system.spawn("periodic", 128, () ->
                Behaviors.<TickMsg>withTimer(timer -> {
                    timer.startFixedRateTimer(TICK_KEY, new Tick(), 0, 50);
                    return Behaviors.setUp(TickCounterActor::new);
                }));

        // block until 4 ticks arrive — no fixed sleep needed
        var reply = new CompletableFuture<Integer>();
        ref.tell(new WaitForTicks(4, reply));
        int count = reply.get(2, TimeUnit.SECONDS);
        assertTrue(count >= 4, "expected >= 4 ticks, got " + count);
    }

    // ---- cancel stops the timer ----

    sealed interface CancelMsg permits StartPeriodicTimer, CancelTimerCmd, WaitForCancelTicks, CancelTick {
    }

    record StartPeriodicTimer(CompletableFuture<Void> ready) implements CancelMsg {
    }

    record CancelTimerCmd(CompletableFuture<Integer> ticksAtCancel) implements CancelMsg {
    }

    record WaitForCancelTicks(int target, CompletableFuture<Integer> reply) implements CancelMsg {
    }

    record CancelTick() implements CancelMsg {
    }

    static class CancelTimerActor extends AbstractBehavior<CancelMsg> {
        private int ticks = 0;
        @org.jspecify.annotations.Nullable CompletableFuture<Integer> pending;
        private int pendingTarget;

        CancelTimerActor(GeneralActorContext<CancelMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<CancelMsg> onReceive(CancelMsg msg) {
            return switch (msg) {
                case StartPeriodicTimer s -> {
                    context.getTimer().startFixedRateTimer(CANCEL_KEY, new CancelTick(), 0, 50);
                    s.ready().complete(null);
                    yield Behaviors.same();
                }
                case CancelTick ignored -> {
                    ticks++;
                    var p = pending;
                    if (p != null && ticks >= pendingTarget) {
                        p.complete(ticks);
                        pending = null;
                    }
                    yield Behaviors.same();
                }
                case CancelTimerCmd c -> {
                    context.getTimer().cancel(CANCEL_KEY);
                    c.ticksAtCancel().complete(ticks);
                    yield Behaviors.same();
                }
                case WaitForCancelTicks w -> {
                    if (ticks >= w.target()) {
                        w.reply().complete(ticks);
                    } else {
                        pending = w.reply();
                        pendingTarget = w.target();
                    }
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void timerStopsAfterCancel() throws Exception {
        var ref = system.spawn("cancel-timer", 128, () -> Behaviors.setUp(CancelTimerActor::new));

        // start timer and wait for it to fire at least 3 times
        var ready = new CompletableFuture<Void>();
        ref.tell(new StartPeriodicTimer(ready));
        ready.get(2, TimeUnit.SECONDS);

        var waitFor3 = new CompletableFuture<Integer>();
        ref.tell(new WaitForCancelTicks(3, waitFor3));
        waitFor3.get(2, TimeUnit.SECONDS);

        // cancel and record tick count at that instant
        var atCancel = new CompletableFuture<Integer>();
        ref.tell(new CancelTimerCmd(atCancel));
        int ticksBefore = atCancel.get(2, TimeUnit.SECONDS);
        assertTrue(ticksBefore >= 3);

        // wait 3 more periods; count must not grow
        Thread.sleep(150);
        var afterWait = new CompletableFuture<Integer>();
        ref.tell(new WaitForCancelTicks(1, afterWait)); // already satisfied immediately
        assertEquals(ticksBefore, afterWait.get(1, TimeUnit.SECONDS),
                "timer must not fire after cancel");
    }

    // ---- timer stops when actor stops ----

    sealed interface ExtMsg permits ExtTick {
    }

    record ExtTick(AtomicInteger counter) implements ExtMsg {
    }

    static class ExtCounterActor extends AbstractBehavior<ExtMsg> {
        ExtCounterActor(GeneralActorContext<ExtMsg> ctx) {
            super(ctx);
        }

        @Override
        public Behavior<ExtMsg> onReceive(ExtMsg msg) {
            return switch (msg) {
                case ExtTick t -> {
                    t.counter().incrementAndGet();
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void timerCancelledWhenActorStops() throws Exception {
        var counter = new AtomicInteger(0);
        var tick = new ExtTick(counter);

        ActorRef<ExtMsg> ref = system.spawn("ext-timer", 64, () ->
                Behaviors.<ExtMsg>withTimer(timer -> {
                    timer.startFixedRateTimer("ext-key", tick, 0, 50);
                    return Behaviors.setUp(ExtCounterActor::new);
                }));

        // wait for a few ticks via AtomicInteger polling (no actor message needed)
        long deadline = System.currentTimeMillis() + 2000;
        while (counter.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(counter.get() >= 3);

        system.stopChild(ref);

        int countAfterStop = counter.get();
        Thread.sleep(150); // 3 × period
        assertEquals(countAfterStop, counter.get(), "timer must not fire after actor stops");
    }

    // ---- withTimer chained with setUp ----

    sealed interface SetupOrderMsg permits CheckSetup {
    }

    record CheckSetup(CompletableFuture<String> reply) implements SetupOrderMsg {
    }

    static class SetupOrderActor extends AbstractBehavior<SetupOrderMsg> {
        private final String tag;

        SetupOrderActor(GeneralActorContext<SetupOrderMsg> ctx, String tag) {
            super(ctx);
            this.tag = tag;
        }

        @Override
        public Behavior<SetupOrderMsg> onReceive(SetupOrderMsg msg) {
            return switch (msg) {
                case CheckSetup c -> {
                    c.reply().complete(tag);
                    yield Behaviors.same();
                }
            };
        }
    }

    @Test
    void withTimerAndSetUpChainWorks() throws Exception {
        ActorRef<SetupOrderMsg> ref = system.spawn("setup-order", 32, () ->
                Behaviors.<SetupOrderMsg>withTimer(timer -> {
                    timer.startSingleTimer("k", new CheckSetup(new CompletableFuture<>()), 60_000);
                    return Behaviors.setUp(ctx -> new SetupOrderActor(ctx, "chained"));
                }));

        var reply = new CompletableFuture<String>();
        ref.tell(new CheckSetup(reply));
        assertEquals("chained", reply.get(2, TimeUnit.SECONDS));
    }
}
