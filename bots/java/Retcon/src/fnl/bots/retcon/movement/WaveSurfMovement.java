package fnl.bots.retcon.movement;

import fnl.bots.framework.arbitration.BodyCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyWaveView;
import fnl.bots.framework.blackboard.EnemyWavesView;
import fnl.bots.framework.blackboard.SelfView;
import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.motion.Drive;
import fnl.bots.framework.motion.MovementPredictor;
import fnl.bots.framework.motion.PredictedState;
import fnl.bots.framework.motion.WallSmoothing;
import fnl.bots.framework.roles.Movement;
import fnl.bots.framework.rules.Rules;

/**
 * True Surfing in the style of BasicSurfer. Each turn it takes the nearest enemy wave that has not reached it yet,
 * predicts where orbiting the wave's origin clockwise and counter-clockwise would put it when the bullet arrives, and
 * goes the way whose GuessFactor has been hit least. It learns from the bullets that hit it, remembered across the
 * rounds of a game. Without a wave to surf it orbits like {@link OrbitMovement}.
 * <p>
 * Book: <a href="https://book.robocode.dev/movement/advanced-evasion/wave-surfing-introduction.html">Wave Surfing
 * Introduction</a> and the True Surfing section of
 * <a href="https://book.robocode.dev/movement/advanced-evasion/wave-surfing-implementations.html">Wave Surfing
 * Implementations</a>. Waves come from {@code EnemyFireSense}; prediction is {@link MovementPredictor}, which follows
 * the server's move-then-turn order (physics fact 6).
 */
public final class WaveSurfMovement implements Movement {

    static final int BINS = 47;
    private static final int MIDDLE = (BINS - 1) / 2;
    /** Danger at GuessFactor 0 before any hit: many simple guns aim head-on. */
    private static final double HEAD_ON_PRIOR = 0.1;
    /** Longest prediction; a wave from across the arena at the slowest bullet speed takes about 90 turns. */
    private static final int MAX_PREDICTION_TURNS = 150;

    private final double preferredDistance;
    private final OrbitMovement fallback;
    private final double[] danger = new double[BINS];
    private final PredictedState predicted = new PredictedState();
    private final double[] turnOut = new double[1];
    private MovementPredictor predictor;
    private int direction = 1;

    public WaveSurfMovement(double preferredDistance, OrbitMovement fallback) {
        this.preferredDistance = preferredDistance;
        this.fallback = fallback;
        danger[MIDDLE] = HEAD_ON_PRIOR;
    }

    @Override
    public void decide(Blackboard bb, BodyCommand out) {
        int now = bb.turn().turn();
        EnemyWavesView waves = bb.enemyWaves();
        learnFromHits(waves, now);

        SelfView self = bb.self();
        EnemyWaveView wave = nearestUnreachedWave(waves, self, now);
        if (wave == null) {
            fallback.decide(bb, out);
            return;
        }
        if (predictor == null) {
            predictor = new MovementPredictor(bb.game().arenaWidth, bb.game().arenaHeight);
        }
        double dangerClockwise = dangerOf(wave, self, now, -1);
        double dangerCounterClockwise = dangerOf(wave, self, now, 1);
        if (dangerClockwise < dangerCounterClockwise) {
            direction = -1;
        } else if (dangerCounterClockwise < dangerClockwise) {
            direction = 1;
        }
        double angle = surfAngle(self.x(), self.y(), wave, direction);
        Drive.toward(self.heading(), angle, Rules.MAX_SPEED, out);
    }

    /** Records the GuessFactor of every bullet that hit this bot this turn. */
    private void learnFromHits(EnemyWavesView waves, int now) {
        for (int i = 0; i < waves.count(); i++) {
            EnemyWaveView wave = waves.get(i);
            if (wave.hitTurn() == now) {
                int index = bin(wave, wave.hitX(), wave.hitY());
                for (int b = 0; b < BINS; b++) {
                    danger[b] += 1.0 / ((b - index) * (b - index) + 1);
                }
            }
        }
    }

    /** The wave whose bullet reaches this bot soonest, among those still more than a bullet step away. */
    private static EnemyWaveView nearestUnreachedWave(EnemyWavesView waves, SelfView self, int now) {
        EnemyWaveView best = null;
        double bestTime = Double.MAX_VALUE;
        for (int i = 0; i < waves.count(); i++) {
            EnemyWaveView wave = waves.get(i);
            if (wave.hitTurn() >= 0) {
                continue;
            }
            double gap = Geom.distance(wave.originX(), wave.originY(), self.x(), self.y()) - wave.radius(now);
            if (gap > wave.bulletSpeed()) {
                double time = gap / wave.bulletSpeed();
                if (time < bestTime) {
                    bestTime = time;
                    best = wave;
                }
            }
        }
        return best;
    }

    /** Danger at the spot this bot reaches when {@code wave} arrives, surfing in {@code orbit} direction. */
    private double dangerOf(EnemyWaveView wave, SelfView self, int now, int orbit) {
        predicted.set(self.x(), self.y(), self.heading(), self.speed());
        int turn = now;
        while (predicted.turns < MAX_PREDICTION_TURNS) {
            double angle = surfAngle(predicted.x, predicted.y, wave, orbit);
            double speed = Drive.toward(predicted, angle, Rules.MAX_SPEED, turnOut);
            predictor.step(predicted, speed, turnOut[0]);
            turn++;
            double distance = Geom.distance(wave.originX(), wave.originY(), predicted.x, predicted.y);
            if (Rules.hitSweepEnd(wave.decisionTurn(), turn, wave.bulletSpeed()) >= distance) {
                break;
            }
        }
        return danger[bin(wave, predicted.x, predicted.y)];
    }

    private double surfAngle(double x, double y, EnemyWaveView wave, int orbit) {
        double angle = OrbitMovement.travelAngle(x, y, wave.originX(), wave.originY(), preferredDistance, orbit);
        return WallSmoothing.smooth(x, y, angle, orbit, OrbitMovement.WALL_STICK, predictor.arenaWidth(),
                predictor.arenaHeight());
    }

    /** GuessFactor bin of the point ({@code x}, {@code y}) on {@code wave}. */
    static int bin(EnemyWaveView wave, double x, double y) {
        double offset = Angles.normalizeRelative(Geom.bearing(wave.originX(), wave.originY(), x, y)
                - wave.directAngle());
        double guessFactor = Geom.clamp(offset * wave.lateralDirection() / wave.maxEscapeAngle(), -1, 1);
        return (int) Math.round((guessFactor + 1) / 2 * (BINS - 1));
    }

    double danger(int bin) {
        return danger[bin];
    }

    int direction() {
        return direction;
    }
}
