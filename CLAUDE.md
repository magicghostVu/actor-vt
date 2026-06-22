# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "org.magicghostvu.actorvt.ActorBasicTest"

# Run a single test method
./gradlew test --tests "org.magicghostvu.actorvt.ActorBasicTest.actorReceivesMessagesInOrder"

# Compile only (no tests)
./gradlew compileJava

# Publish to GitLab Maven registry (requires deploy.token in gradle.properties)
./gradlew publish
```

## Architecture

This is a Java 21 actor framework library (`actor-vt`) built on virtual threads. The core design is a typed, hierarchical actor system where each actor runs its message loop on a virtual thread and processes messages sequentially.

### Key abstractions

**`ActorSystem`** — root of the hierarchy. Entry point for spawning top-level actors. Call `destroy()` to shut down; this stops all children before shutting down the thread pool (important: without stopping children first, threads block forever on `messageQueue.take()`).

**`ActorRef<T>`** — typed handle to an actor. `tell(msg)` enqueues a message (blocks if the queue is full; throws if interrupted). `trySend(msg)` is the non-blocking variant — uses `offer()` and returns `false` if the queue is full, instead of blocking. Both throw `IllegalArgumentException` if the actor is not active. `context` field goes `null` when the actor stops — callers check this to detect termination.

**`ActorContext`** (sealed: `ActorSystem | GeneralActorContext`) — common parent for the actor hierarchy. Each actor context holds a `refToChild` map tracking its children.

**`GeneralActorContext<Protocol>`** — the live context of a running actor. Passed to `AbstractBehavior` constructors. Provides `spawn()`, `stopChild()`, `getTimer()`, and `self()`. The message loop runs here; unhandled exceptions cause the actor to stop itself.

**`Behavior<T>`** (sealed: `AbstractBehavior | SetUpBehavior | TimerBehavior | Same | Stopped`) — the behavior type returned by `onReceive`. `SetUpBehavior` and `TimerBehavior` are wrapper types unwrapped at actor start and on state transition via `unwrapBehavior()`.

**`AbstractBehavior<T>`** — extend this to write actors. Implement `onReceive(T msg)` returning the next behavior.

**`TimerManData<T>`** — timer manager accessed via `context.getTimer()`. Supports `startSingleTimer`, `startFixedRateTimer`, and `cancel`. Each timer registration gets a unique generation stamp (`nextGeneration++`) stored on `TimerData`; stale `DelayMsg` entries in the queue are dropped by checking `findTimerData(key) == null` (cancelled) or `current.generation != delayMsg.generation()` (restarted with same key). **Not thread-safe** — all timer calls must happen on the actor's own thread (inside `onReceive` or the behavior factory).

### Package structure

```
org.magicghostvu.actorvt              ActorRef
  behavior/                           Behavior (sealed), AbstractBehavior, Behaviors,
                                        Same, Stopped, SetUpBehavior, TimerBehavior,
                                        ActorVTFunc
  context/                            ActorContext (sealed), ActorSystem,
                                        GeneralActorContext, ActorVTSupplier, TypeStop
    msg/                              DelayMsg, SystemMsg
    timer/                            TimerManData, TimerData,
                                        SingleTimerData, PeriodicTimerData
```

`ActorVTFunc` and `ActorVTSupplier` are internal `@FunctionalInterface` types (analogues of `Function` and `Supplier`). `TypeStop`, `DelayMsg`, and `SystemMsg` are internal implementation details.

### Behavior factory pattern

Actors are created via `Supplier<Behavior<T>>` passed to `spawn()`. Three wrappers compose:

```java
// simple
Behaviors.setUp(MyActor::new)

// with timers set up before the actor starts
Behaviors.withTimer(timer -> {
    timer.startFixedRateTimer(KEY, new Tick(), 0, 100);
    return Behaviors.setUp(MyActor::new);
})
```

Both `SetUpBehavior` and `TimerBehavior` are unwrapped by `GeneralActorContext.unwrapBehavior()` before the actor loop starts and also when `onReceive` returns a wrapped behavior.

### Null safety

All packages under `org.magicghostvu.actorvt` are annotated `@NullMarked` via `package-info.java`. NullAway enforces this at compile time (configured as `ERROR` severity in `build.gradle.kts`). JDK 21 requires the `-XDaddTypeAnnotationsToSymbol=true` compiler flag for NullAway to work.

### Logging

SLF4J API with log4j2 as the runtime implementation (`log4j-slf4j-impl`). Log config is at `log4j2.xml` in the project root; test runs load it via `-Dlog4j.configurationFile`. Logger names used: `actor-system`, `actor-context`, `actor-timer`.

### Testing

Tests use JUnit 5 with `CompletableFuture`-based synchronization — never fixed sleeps for waiting on actor state. The `awaitStopped(ref)` helper polls `ref.context != null` at 2 ms intervals up to a 2-second deadline. Each test creates its own `ActorSystem` in `@BeforeEach` and calls `destroy()` in `@AfterEach`.
