package fnl.bots.retcon.movement;

import fnl.bots.framework.arbitration.BodyCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.blackboard.EnemyWaveView;
import fnl.bots.framework.blackboard.EnemyWavesView;
import fnl.bots.framework.blackboard.SelfView;
import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.motion.Drive;
import fnl.bots.framework.motion.WallSmoothing;
import fnl.bots.framework.roles.Movement;
import fnl.bots.framework.rules.Rules;

import java.util.Random;

/**
 * Circles the target at full speed, perpendicular to it, steering in or out to hold a preferred distance, and slides
 * along walls in the circling direction. It reverses when a wall turns it too far off its course, and at random when
 * the target fires, so a gun cannot assume it keeps going one way.
 * <p>
 * Book: <a href="https://book.robocode.dev/movement/basic/distancing.html">Distancing</a> (preferred range),
 * <a href="https://book.robocode.dev/movement/basic/wall-avoidance-wall-smoothing.html">Wall Avoidance &amp; Wall
 * Smoothing</a>, and <a href="https://book.robocode.dev/movement/simple-evasion/random-movement.html">Random
 * Movement</a> (reversing on the enemy's shots).
 */
public final class OrbitMovement implements Movement {

    /** How far ahead wall smoothing looks, as in BasicSurfer. */
    static final double WALL_STICK = 160;
    /** Largest steer toward or away from the target, in radians off perpendicular. */
    private static final double MAX_DISTANCE_STEER = Math.toRadians(30);
    /** A wall pushing the course this far off the orbit makes the bot reverse instead. */
    private static final double MAX_SMOOTHING = Math.PI / 2;
    private static final double REVERSE_ON_FIRE_CHANCE = 0.25;
    private static final int MIN_TURNS_BETWEEN_REVERSALS = 10;

    private final double preferredDistance;
    private final Random random;
    private int direction = 1;
    private int lastReversalTurn = Integer.MIN_VALUE / 2;
    private int lastWaveTurn = -1;

    public OrbitMovement(double preferredDistance, long seed) {
        this.preferredDistance = preferredDistance;
        this.random = new Random(seed);
    }

    @Override
    public void decide(Blackboard bb, BodyCommand out) {
        SelfView self = bb.self();
        int now = bb.turn().turn();
        double arenaWidth = bb.game().arenaWidth;
        double arenaHeight = bb.game().arenaHeight;
        EnemyView target = bb.targetEnemy();
        double centerX = target != null ? target.x() : arenaWidth / 2;
        double centerY = target != null ? target.y() : arenaHeight / 2;

        if (target != null && enemyJustFired(bb.enemyWaves(), target.id(), now)
                && random.nextDouble() < REVERSE_ON_FIRE_CHANCE) {
            reverse(now);
        }

        double angle = travelAngle(self, centerX, centerY, direction);
        double smoothed = WallSmoothing.smooth(self.x(), self.y(), angle, direction, WALL_STICK, arenaWidth,
                arenaHeight);
        if (Math.abs(Angles.normalizeRelative(smoothed - angle)) > MAX_SMOOTHING && reverse(now)) {
            angle = travelAngle(self, centerX, centerY, direction);
            smoothed = WallSmoothing.smooth(self.x(), self.y(), angle, direction, WALL_STICK, arenaWidth,
                    arenaHeight);
        }
        Drive.toward(self.heading(), smoothed, Rules.MAX_SPEED, out);
    }

    /**
     * Perpendicular to the line from the centre to this bot, turned toward the centre when too far and away when too
     * close. The same convention as BasicSurfer: from "away from the centre", a quarter turn in {@code direction}.
     */
    double travelAngle(SelfView self, double centerX, double centerY, int direction) {
        return travelAngle(self.x(), self.y(), centerX, centerY, preferredDistance, direction);
    }

    /** {@link #travelAngle(SelfView, double, double, int)} for any position, e.g. a predicted one. */
    static double travelAngle(double x, double y, double centerX, double centerY, double preferredDistance,
                              int direction) {
        double away = Geom.bearing(centerX, centerY, x, y);
        double distance = Geom.distance(centerX, centerY, x, y);
        double error = (distance - preferredDistance) / preferredDistance;
        double steer = Geom.clamp(error, -1, 1) * MAX_DISTANCE_STEER;
        return Angles.normalizeAbsolute(away + direction * (Math.PI / 2 + steer));
    }

    int direction() {
        return direction;
    }

    private boolean reverse(int now) {
        if (now - lastReversalTurn < MIN_TURNS_BETWEEN_REVERSALS) {
            return false;
        }
        direction = -direction;
        lastReversalTurn = now;
        return true;
    }

    /** Whether a wave from {@code shooterId} appeared this turn (it was decided the turn before). */
    private boolean enemyJustFired(EnemyWavesView waves, int shooterId, int now) {
        for (int i = 0; i < waves.count(); i++) {
            EnemyWaveView wave = waves.get(i);
            if (wave.shooterId() == shooterId && wave.decisionTurn() == now - 1 && lastWaveTurn != now) {
                lastWaveTurn = now;
                return true;
            }
        }
        return false;
    }
}
