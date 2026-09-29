package fnl.bots.framework.geom;

/**
 * The single angle convention of the framework core: radians, counter-clockwise from east (the positive x-axis).
 * Absolute headings are normalized to [0, 2π); relative angles (turns, differences) to (−π, π].
 * <p>
 * Platform conventions (Tank Royale degrees, classic Robocode clockwise-from-north) are converted in the adapters
 * only. See <a href="https://book.robocode.dev/physics/coordinates-and-angles.html">Coordinate Systems &amp; Angles</a>.
 */
public final class Angles {

    public static final double TWO_PI = 2 * Math.PI;

    private Angles() {
    }

    /** Normalizes an absolute heading into [0, 2π). */
    public static double normalizeAbsolute(double angle) {
        double a = angle % TWO_PI;
        if (a < 0) {
            a += TWO_PI;
        }
        return a >= TWO_PI ? 0 : a;
    }

    /** Normalizes a relative angle (a turn or a difference) into (−π, π]. */
    public static double normalizeRelative(double angle) {
        double a = angle % TWO_PI;
        if (a <= -Math.PI) {
            a += TWO_PI;
        } else if (a > Math.PI) {
            a -= TWO_PI;
        }
        return a;
    }

    /** Converts to degrees for logs and debug output only; never use degrees in strategy code. */
    public static double toDegreesForDisplay(double radians) {
        return Math.toDegrees(radians);
    }
}
