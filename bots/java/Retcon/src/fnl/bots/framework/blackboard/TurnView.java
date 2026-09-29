package fnl.bots.framework.blackboard;

/** Clock and time budget of the current turn. */
public interface TurnView {

    int round();

    int turn();

    /** Server turn timeout in microseconds, read from the platform at runtime. */
    int turnTimeoutMicros();

    /** Microseconds of compute this bot allows itself per turn: a configured share of the turn timeout. */
    int computeBudgetMicros();

    /** {@link System#nanoTime()} when the pipeline started this turn; heavy stages use it to stop early. */
    long turnStartNanos();

    /** True when this turn's compute has used up {@link #computeBudgetMicros()}. */
    default boolean overBudget() {
        return (System.nanoTime() - turnStartNanos()) / 1000 >= computeBudgetMicros();
    }
}
