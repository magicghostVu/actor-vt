# actor-vt

A lightweight, typed actor framework for Java 21, built on virtual threads. Each actor processes messages sequentially on its own virtual thread, with no manual locking required inside behavior code.

## Requirements

- Java 21+


The library exposes SLF4J as an API dependency. Add a logging implementation (e.g., log4j2, logback) at runtime in your own project.

---

## Core concepts

| Concept | Description |
|---|---|
| `ActorSystem` | Root of the actor hierarchy. Create once, destroy on shutdown. |
| `ActorRef<T>` | Typed handle to an actor. The only way to send messages (`tell`). |
| `AbstractBehavior<T>` | Extend this to write an actor. Implement `onReceive` to handle messages. |
| `Behaviors` | Factory class for creating `Behavior` values returned from `onReceive`. |
| `GeneralActorContext<T>` | Live context of a running actor. Available as `context` inside `AbstractBehavior`. Used to spawn children, access timers, and get `self()`. |
| `TimerManData<T>` | Timer manager. Obtained via `context.getTimer()` inside an actor. |

---

## Quick start

```java
// 1. Define your message protocol as a sealed interface
sealed interface CountMsg permits Increment, GetCount {}
record Increment() implements CountMsg {}
record GetCount(CompletableFuture<Integer> reply) implements CountMsg {}

// 2. Extend AbstractBehavior
class CounterActor extends AbstractBehavior<CountMsg> {
    private int count = 0;

    CounterActor(GeneralActorContext<CountMsg> ctx) {
        super(ctx);
    }

    @Override
    public Behavior<CountMsg> onReceive(CountMsg msg) {
        return switch (msg) {
            case Increment ignored -> { count++; yield Behaviors.same(); }
            case GetCount g -> { g.reply().complete(count); yield Behaviors.same(); }
        };
    }
}

// 3. Create the system, spawn an actor, send messages
ActorSystem system = new ActorSystem();

ActorRef<CountMsg> ref = system.spawn("counter", 64,
        () -> Behaviors.setUp(CounterActor::new));

ref.tell(new Increment());
ref.tell(new Increment());

var reply = new CompletableFuture<Integer>();
ref.tell(new GetCount(reply));

System.out.println(reply.get()); // 2

system.destroy();
```

---

## API reference

### `ActorSystem`

```java
// Create with its own virtual-thread pool
new ActorSystem()
new ActorSystem(String rootPath)

// Inject an external executor (pool is not shut down on destroy)
new ActorSystem(ExecutorService threadPool, String rootPath)
```

| Method | Description |
|---|---|
| `spawn(name, queueCapacity, factory)` | Creates a top-level actor. `factory` is called once to produce the initial behavior. |
| `stopChild(ActorRef)` | Stops a direct child actor synchronously. |
| `destroy()` | Stops all actors and shuts down the thread pool. Safe to call multiple times. |

**`queueCapacity`**: maximum number of messages buffered for the actor. `tell()` blocks the caller if the queue is full. Queues larger than 1024 become unbounded (`LinkedBlockingQueue`).

---

### `ActorRef<T>`

Obtained from `spawn`. Hold and share freely — it is thread-safe.

| Member | Description |
|---|---|
| `tell(T message)` | Enqueues a message. Throws `IllegalArgumentException` if the actor has stopped. |
| `name` | The name given at spawn time. |
| `path` | Full hierarchical path (e.g., `/parent/child`). |
| `context` | `null` when the actor has stopped. Useful for polling termination. |

---

### `AbstractBehavior<T>`

```java
class MyActor extends AbstractBehavior<MyMsg> {

    MyActor(GeneralActorContext<MyMsg> ctx) {
        super(ctx);   // stores ctx as protected field `context`
    }

    @Override
    public Behavior<MyMsg> onReceive(MyMsg msg) {
        // return next behavior
    }
}
```

The `context` field (type `GeneralActorContext<T>`) gives access to:

| Method | Description |
|---|---|
| `context.spawn(name, capacity, factory)` | Spawn a child actor. |
| `context.stopChild(ActorRef)` | Stop a child actor. |
| `context.getTimer()` | Access the timer manager for this actor. |
| `context.self()` | Get the actor's own `ActorRef`. |
| `context.getPath()` | Get the actor's path string. |

---

### `Behaviors` factory

All values returned from `onReceive` must come from this class or be a new `AbstractBehavior` instance.

| Method | Description |
|---|---|
| `Behaviors.same()` | Keep the current behavior unchanged. |
| `Behaviors.stopped()` | Stop this actor after handling the current message. |
| `Behaviors.setUp(factory)` | Wrap a behavior factory. The factory receives `GeneralActorContext` and must return an `AbstractBehavior`. |
| `Behaviors.withTimer(timerFunc)` | Wrap a timer-setup factory. The function receives `TimerManData` to register timers, then must return another `Behavior` (typically `Behaviors.setUp`). |

`SetUp` and `withTimer` can be chained and are unwrapped at actor start time:

```java
// outer is unwrapped first → inner setUp receives the context
Behaviors.<MyMsg>withTimer(timer -> {
    timer.startFixedRateTimer("tick", new Tick(), 0, 1000);
    return Behaviors.setUp(MyActor::new);
})
```

`onReceive` can also return `Behaviors.setUp(...)` or `Behaviors.withTimer(...)` to transition to a new behavior — it is unwrapped before the next message arrives.

---

### `TimerManData<T>`

Obtained inside an actor via `context.getTimer()`. All timer messages are delivered to the same actor as regular messages — there is no callback thread.

> **Thread safety**: `TimerManData` and `TimerData` are **not thread-safe**. All calls — `startSingleTimer`, `startFixedRateTimer`, `cancel`, `cancelAll`, and `TimerData.cancel()` — must be made from the actor's own thread: inside `onReceive`, or inside the `Behaviors.withTimer` / `Behaviors.setUp` factory that runs at actor start. Never call timer methods from outside the actor (e.g., from another actor or an external thread).

#### Single-shot timer

Fires once after `delayMillis`. Cancelled automatically when it fires.

```java
// explicit key — restarting with the same key cancels the previous timer
timer.startSingleTimer(Object key, T message, long delayMillis)

// use the message instance itself as the key
timer.startSingleTimer(T message, long delayMillis)
```

#### Fixed-rate timer

Fires every `period` milliseconds forever until cancelled.

```java
// explicit key
timer.startFixedRateTimer(Object key, T message, long initDelayMillis, long period)

// message as key
timer.startFixedRateTimer(T message, long initDelayMillis, long period)
```

`initDelayMillis = 0` fires the first tick immediately (no initial delay).

#### Cancellation

```java
timer.cancel(Object key)   // cancel one timer by key
timer.cancelAll()          // cancel all timers for this actor
```

All timers are cancelled automatically when the actor stops.

#### Restarting a timer inside a handler

Calling `startSingleTimer` with the same key inside `onReceive` (after the timer fires) is safe — the old in-flight `DelayMsg` (if any) is discarded by a generation check and the handler sees only the new timer.

---

## Behavior lifecycle

```
spawn() called
     │
     ▼
factory.get()          ← Behaviors.withTimer / setUp are unwrapped here
     │
     ▼
actor loop starts
     │
     ├─ message arrives → onReceive(msg) → returns:
     │       Behaviors.same()      → keep current behavior
     │       Behaviors.stopped()   → stop actor
     │       new MyBehavior(ctx)   → switch to new behavior
     │       Behaviors.setUp(...)  → unwrap, then switch
     │
     └─ unhandled exception → actor stops (crash isolation)
```

When an actor stops (for any reason):
- All child actors are stopped first (depth-first).
- All timers are cancelled.
- `ActorRef.context` is set to `null`.
- The actor is removed from its parent's child map.

---

## Actor hierarchy and crash isolation

Actors form a tree. `system.spawn(...)` creates a root-level actor. Inside an actor, `context.spawn(...)` creates a child.

```java
class ParentActor extends AbstractBehavior<ParentMsg> {
    @Nullable ActorRef<ChildMsg> child;

    @Override
    public Behavior<ParentMsg> onReceive(ParentMsg msg) {
        return switch (msg) {
            case SpawnChild ignored -> {
                child = context.spawn("child", 32, () -> Behaviors.setUp(ChildActor::new));
                yield Behaviors.same();
            }
            case KillChild ignored -> {
                if (child != null) context.stopChild(child);
                yield Behaviors.same();
            }
        };
    }
}
```

Crash isolation: if an actor throws an unhandled exception from `onReceive`, only that actor (and its children) stop — the rest of the system keeps running.

---

## Detecting actor termination

`ActorRef.context` becomes `null` when an actor stops. Poll it to wait for termination:

```java
static void awaitStopped(ActorRef<?> ref) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000;
    while (ref.context != null && System.currentTimeMillis() < deadline) {
        Thread.sleep(5);
    }
    if (ref.context != null) throw new IllegalStateException("actor did not stop in time");
}
```

Alternatively, have the actor complete a `CompletableFuture` just before returning `Behaviors.stopped()`.

---

## Full timer example

```java
sealed interface GameMsg permits Tick, Pause, Resume {}
record Tick() implements GameMsg {}
record Pause() implements GameMsg {}
record Resume() implements GameMsg {}

class GameLoopActor extends AbstractBehavior<GameMsg> {
    static final Object TICK_KEY = "game-tick";
    private int frame = 0;

    GameLoopActor(GeneralActorContext<GameMsg> ctx) { super(ctx); }

    @Override
    public Behavior<GameMsg> onReceive(GameMsg msg) {
        return switch (msg) {
            case Tick ignored -> {
                frame++;
                System.out.println("frame " + frame);
                yield Behaviors.same();
            }
            case Pause ignored -> {
                context.getTimer().cancel(TICK_KEY);
                yield Behaviors.same();
            }
            case Resume ignored -> {
                context.getTimer().startFixedRateTimer(TICK_KEY, new Tick(), 0, 16);
                yield Behaviors.same();
            }
        };
    }
}

ActorSystem system = new ActorSystem();

ActorRef<GameMsg> loop = system.spawn("game-loop", 128, () ->
        Behaviors.<GameMsg>withTimer(timer -> {
            timer.startFixedRateTimer(GameLoopActor.TICK_KEY, new Tick(), 0, 16);
            return Behaviors.setUp(GameLoopActor::new);
        }));

Thread.sleep(100);
loop.tell(new Pause());
Thread.sleep(200);          // no ticks during pause
loop.tell(new Resume());
Thread.sleep(100);

system.destroy();
```

---

## Shutdown

Always call `system.destroy()` when done. It stops all actors in the tree and shuts down the virtual-thread pool. Not calling it will leave daemon threads alive until JVM exit.

```java
ActorSystem system = new ActorSystem();
try {
    // ... application code
} finally {
    system.destroy();
}
```
