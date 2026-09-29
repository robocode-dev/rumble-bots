package fnl.bots.framework.stats;

import fnl.bots.framework.motion.MovementPredictor;
import fnl.bots.framework.motion.PredictedState;
import fnl.bots.framework.rules.Rules;
import fnl.bots.framework.sense.TurnInput;

/**
 * Checks {@link MovementPredictor} (and so {@link Rules#BODY_MOVES_BEFORE_TURNING}, {@link Rules#nextSpeed} and
 * {@link Rules#constrainBotPosition}) against the live server: each turn it predicts this bot's own position,
 * heading and speed from the previous turn's state and body intent, and compares them with what the server reports.
 * Turns after a skipped turn, a bot collision or while disabled ({@link Rules#isDisabled}) are not checked: the
 * intent was not applied, or the bot was pushed or held. A skip is reported a turn late, so the turn it spoiled is
 * reclassified then.
 * Allocation-free.
 */
public final class MovementProbe {

    private static final double POSITION_EPSILON = 1e-6;
    private static final double HEADING_EPSILON = 1e-6;
    private static final int MAX_MISMATCHES = 5;

    private MovementPredictor predictor;
    private final PredictedState predicted = new PredictedState();
    private boolean pending;
    private int pendingRound;
    private int pendingTurn;
    private boolean pendingDisabled;
    private boolean lastWasMismatch;
    private boolean lastMismatchListed;
    private int lastMismatchTextLength;

    public long turnsChecked;
    public long matches;
    public long wallHitsChecked;
    public long notChecked;
    public final StringBuilder mismatches = new StringBuilder();
    private int mismatchCount;

    public void setArena(double arenaWidth, double arenaHeight) {
        predictor = new MovementPredictor(arenaWidth, arenaHeight);
    }

    /** The bot sends this body intent in response to the turn in {@code in}. */
    public void onIntent(TurnInput in, double targetSpeed, double bodyTurnRate) {
        if (predictor == null) {
            return;
        }
        predicted.set(in.x, in.y, in.heading, in.speed);
        predictor.step(predicted, targetSpeed, bodyTurnRate);
        pending = true;
        pendingRound = in.round;
        pendingTurn = in.turn;
        pendingDisabled = Rules.isDisabled(in.energy);
    }

    /** Compares the new turn's own state with the prediction made on the previous turn. */
    public void observe(TurnInput in) {
        if (!pending) {
            return;
        }
        pending = false;
        if (in.skippedTurns > 0 && lastWasMismatch) {
            turnsChecked--;
            notChecked++;
            if (lastMismatchListed) {
                mismatchCount--;
                mismatches.setLength(lastMismatchTextLength);
            }
        }
        lastWasMismatch = false;
        if (in.round != pendingRound || in.turn != pendingTurn + 1 || in.skippedTurns > 0 || in.collisionCount > 0
                || pendingDisabled || Rules.isDisabled(in.energy)) {
            notChecked++;
            return;
        }
        turnsChecked++;
        if (predicted.hitWall) {
            wallHitsChecked++;
        }
        double headingError = Math.abs(Math.IEEEremainder(in.heading - predicted.heading, 2 * Math.PI));
        boolean ok = Math.abs(in.x - predicted.x) <= POSITION_EPSILON
                && Math.abs(in.y - predicted.y) <= POSITION_EPSILON
                && Math.abs(in.speed - predicted.speed) <= POSITION_EPSILON
                && headingError <= HEADING_EPSILON;
        if (ok) {
            matches++;
            return;
        }
        lastWasMismatch = true;
        lastMismatchTextLength = mismatches.length();
        lastMismatchListed = mismatchCount < MAX_MISMATCHES;
        if (lastMismatchListed) {
            mismatchCount++;
            if (!mismatches.isEmpty()) {
                mismatches.append("; ");
            }
            mismatches.append("turn ").append(in.turn).append(" predicted ").append(predicted.x).append(',')
                    .append(predicted.y).append(" v ").append(predicted.speed).append(" got ").append(in.x)
                    .append(',').append(in.y).append(" v ").append(in.speed);
        }
    }
}
