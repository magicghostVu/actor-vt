package org.magicghostvu.actorvt.context;

@FunctionalInterface
public interface ActorVTSupplier<T> {
    public T get();
}
