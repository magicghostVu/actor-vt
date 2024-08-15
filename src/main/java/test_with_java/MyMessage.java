package test_with_java;

public sealed interface MyMessage permits ChildMsg1, ChildMsg2, KillMyActor {
}
