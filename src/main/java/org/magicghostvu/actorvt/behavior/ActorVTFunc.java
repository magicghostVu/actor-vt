package org.magicghostvu.actorvt.behavior;

@FunctionalInterface
public interface ActorVTFunc<A,B> {
    public B apply(A a);
}
