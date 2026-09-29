package fnl.bots.framework.motion;

import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.rules.Rules;

/**
 * Turns a desired travel angle away from the walls: a point {@code stick} units ahead must stay inside the arena
 * inset by {@link #MARGIN}; otherwise the angle rotates in small steps toward {@code orientation} until it does.
 * <p>
 * Implements <a href="https://book.robocode.dev/movement/basic/wall-avoidance-wall-smoothing.html">Wall Avoidance
 * &amp; Wall Smoothing</a>, with the rotation direction as a parameter so an orbit or a surfer keeps circling the
 * same way along a wall. Angles are radians, counter-clockwise from east. Allocation-free.
 */
public final class WallSmoothing {

    /** Inset of the safe rectangle: the server keeps a bot's centre this far from a wall. */
    public static final double MARGIN = Rules.BOT_HIT_RADIUS;
    private static final double STEP = Math.toRadians(2);
    private static final int MAX_STEPS = 180; // a full half turn

    private WallSmoothing() {
    }

    /**
     * Returns {@code angle}, rotated toward {@code orientation} (+1 counter-clockwise, −1 clockwise) until a point
     * {@code stick} units ahead of ({@code x}, {@code y}) is inside the safe rectangle.
     */
    public static double smooth(double x, double y, double angle, int orientation, double stick, double arenaWidth,
                                double arenaHeight) {
        double a = angle;
        for (int i = 0; i < MAX_STEPS; i++) {
            if (isSafe(x + Math.cos(a) * stick, y + Math.sin(a) * stick, arenaWidth, arenaHeight)) {
                return Angles.normalizeAbsolute(a);
            }
            a += orientation * STEP;
        }
        return Angles.normalizeAbsolute(a);
    }

    public static boolean isSafe(double x, double y, double arenaWidth, double arenaHeight) {
        return x >= MARGIN && x <= arenaWidth - MARGIN && y >= MARGIN && y <= arenaHeight - MARGIN;
    }
}
