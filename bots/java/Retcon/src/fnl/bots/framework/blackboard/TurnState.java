package fnl.bots.framework.blackboard;

/** Mutable {@link TurnView}, written only by its Sense-phase owner. */
public final class TurnState implements TurnView {

    public int round;
    public int turn;
    public int turnTimeoutMicros;
    public int computeBudgetMicros;
    public long turnStartNanos;

    @Override
    public int round() {
        return round;
    }

    @Override
    public int turn() {
        return turn;
    }

    @Override
    public int turnTimeoutMicros() {
        return turnTimeoutMicros;
    }

    @Override
    public int computeBudgetMicros() {
        return computeBudgetMicros;
    }

    @Override
    public long turnStartNanos() {
        return turnStartNanos;
    }
}
