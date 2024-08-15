package test_with_java;

import kotlin.Unit;

import java.util.concurrent.CompletableFuture;

public final class KillMyActor implements MyMessage {

    CompletableFuture<Unit> repTo = new CompletableFuture<>();

    public KillMyActor() {}

}
