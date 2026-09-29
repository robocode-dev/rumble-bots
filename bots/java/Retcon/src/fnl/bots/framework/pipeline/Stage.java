package fnl.bots.framework.pipeline;

import fnl.bots.framework.blackboard.Blackboard;

/**
 * A step of the Model or Decide phase. A stage claims the blackboard slots and actuators it writes when it is
 * constructed, and must not allocate in {@link #run}.
 */
public interface Stage {

    void run(Blackboard bb);

    /** Called when a new round starts, before its first turn. */
    default void onRoundStart() {
    }
}
