package org.magicghostvu.actorvt.context.msg;

public record DelayMsg<T>(T msg, Object key, int generation) implements SystemMsg {
}
