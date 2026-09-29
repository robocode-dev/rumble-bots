package fnl.bots.framework.motion;

import fnl.bots.framework.arbitration.BodyCommand;
import fnl.bots.framework.geom.Angles;

/**
 * Drives toward an absolute angle, backing up when the angle is behind the bot, so the body never turns more than
 * 90°. This is the usual GoTo core of
 * <a href="https://book.robocode.dev/movement/basic/movement-fundamentals-goto.html">Movement Fundamentals &amp;
 * GoTo</a>. Radians, counter-clockwise from east. Allocation-free.
 */
public final class Drive {

    private Drive() {
    }

    /** Sets {@code out} to travel along {@code angle} at {@code speed} (≥ 0) from a body facing {@code heading}. */
    public static void toward(double heading, double angle, double speed, BodyCommand out) {
        double turn = Angles.normalizeRelative(angle - heading);
        if (Math.abs(turn) > Math.PI / 2) {
            out.turnRate = Angles.normalizeRelative(turn + Math.PI);
            out.targetSpeed = -speed;
        } else {
            out.turnRate = turn;
            out.targetSpeed = speed;
        }
    }

    /** The same decision for a predicted body: returns the target speed and stores the turn rate in {@code out}. */
    public static double toward(PredictedState state, double angle, double speed, double[] turnOut) {
        double turn = Angles.normalizeRelative(angle - state.heading);
        if (Math.abs(turn) > Math.PI / 2) {
            turnOut[0] = Angles.normalizeRelative(turn + Math.PI);
            return -speed;
        }
        turnOut[0] = turn;
        return speed;
    }
}
