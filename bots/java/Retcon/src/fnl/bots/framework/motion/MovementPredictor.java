package fnl.bots.framework.motion;

import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.rules.Rules;

/**
 * Steps a bot's body forward one turn at a time exactly as the server does
 * ({@link Rules#BODY_MOVES_BEFORE_TURNING}): new speed, move along the old heading, clamp at the walls
 * ({@link Rules#constrainBotPosition}, speed 0 on a hit), then turn within the new speed's limit.
 * <p>
 * Implements <a href="https://book.robocode.dev/targeting/predictive-targeting/precise-prediction.html">Precise
 * Prediction</a>, the shared engine of
 * <a href="https://book.robocode.dev/movement/advanced-evasion/wave-surfing-implementations.html">Wave Surfing
 * Implementations</a>. One instance per thread: it keeps a small scratch buffer, so stepping never allocates.
 */
public final class MovementPredictor {

    private final double arenaWidth;
    private final double arenaHeight;
    private final double[] constrained = new double[2];

    public MovementPredictor(double arenaWidth, double arenaHeight) {
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
    }

    /** Advances {@code state} by one turn under the given body intent (turn rate in radians per turn). */
    public void step(PredictedState state, double targetSpeed, double turnRate) {
        state.speed = Rules.nextSpeed(state.speed, targetSpeed);
        double oldX = state.x;
        double oldY = state.y;
        double x = oldX + Math.cos(state.heading) * state.speed;
        double y = oldY + Math.sin(state.heading) * state.speed;
        state.heading = Angles.normalizeAbsolute(state.heading + Rules.limitBodyTurnRate(turnRate, state.speed));
        Rules.constrainBotPosition(oldX, oldY, x, y, arenaWidth, arenaHeight, constrained);
        if (constrained[0] != x || constrained[1] != y) {
            state.speed = 0;
            state.hitWall = true;
        }
        state.x = constrained[0];
        state.y = constrained[1];
        state.turns++;
    }

    public double arenaWidth() {
        return arenaWidth;
    }

    public double arenaHeight() {
        return arenaHeight;
    }
}
