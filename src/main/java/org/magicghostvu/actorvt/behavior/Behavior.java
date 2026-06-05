package org.magicghostvu.actorvt.behavior;

public sealed abstract class Behavior<T>
        permits AbstractBehavior, SetUpBehavior, TimerBehavior, Same, Stopped {
}
