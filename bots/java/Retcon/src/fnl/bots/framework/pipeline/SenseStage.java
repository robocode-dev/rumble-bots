package fnl.bots.framework.pipeline;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.sense.TurnInput;

/** A step of the Sense phase: the only kind of stage that reads the raw {@link TurnInput}. */
public interface SenseStage {

    void sense(TurnInput in, Blackboard bb);

    /** Called when a new round starts, before its first turn. */
    default void onRoundStart() {
    }
}
