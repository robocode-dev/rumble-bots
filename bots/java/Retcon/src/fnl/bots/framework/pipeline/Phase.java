package fnl.bots.framework.pipeline;

/** The four phases of every turn, run in this order. */
public enum Phase {
    /** Turn the platform's raw input into derived state on the blackboard. */
    SENSE,
    /** Update models and collected data from the derived state. */
    MODEL,
    /** Role strategies write commands for the actuators they own. */
    DECIDE,
    /** The arbiter resolves the commands into one intent. */
    ARBITRATE
}
