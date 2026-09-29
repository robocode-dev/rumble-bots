package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemiesView;
import fnl.bots.framework.blackboard.EnemyTable;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.blackboard.EnemyWave;
import fnl.bots.framework.blackboard.EnemyWaves;
import fnl.bots.framework.blackboard.SelfView;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.pipeline.SenseStage;
import fnl.bots.framework.rules.Rules;
import fnl.bots.framework.sense.BulletRecord;
import fnl.bots.framework.sense.TurnInput;

/**
 * Sense: turns enemy energy drops into waves. Must run after {@link SelfSense} and {@link EnemySense}, which it
 * reads.
 * <p>
 * Implements <a href="https://book.robocode.dev/movement/advanced-evasion/dodging-bullets.html">Dodging Bullets</a>
 * (hearing the shot) and the wave creation step of
 * <a href="https://book.robocode.dev/movement/advanced-evasion/wave-surfing-introduction.html">Wave Surfing</a>,
 * with Tank Royale's timing from physics facts 4 and 5: the scan of turn d shows a shot decided at d − 1, fired from
 * the position scanned at d − 1. Before testing a drop it removes the energy changes it already knows about: ram and
 * wall damage from the same turn, and bullet hits reported up to the previous turn. An estimate of the shooter's gun
 * heat rejects drops that cannot be shots.
 * <p>
 * It also matches the bullets that hit this bot to their waves, so movement can learn where it was hit.
 */
public final class EnemyFireSense implements SenseStage {

    /** Tolerance on energy comparisons; scans report energy as doubles computed the same way as ours. */
    private static final double ENERGY_EPSILON = 1e-6;
    /** The server clamps a bot that hits a wall to exactly {@link Rules#BOT_HIT_RADIUS} from it. */
    private static final double WALL_EPSILON = 1e-6;

    private final EnemyWaves waves;
    private final int[] prevTurn = new int[EnemyTable.CAPACITY];
    private final double[] prevX = new double[EnemyTable.CAPACITY];
    private final double[] prevY = new double[EnemyTable.CAPACITY];
    private final double[] prevEnergy = new double[EnemyTable.CAPACITY];
    private final double[] prevSpeed = new double[EnemyTable.CAPACITY];
    /** Known energy change not yet seen in a scan: negative for damage, positive for bonuses. */
    private final double[] knownChange = new double[EnemyTable.CAPACITY];
    /** Estimated gun heat after turn {@link #heatTurn}; the server cools it every turn the gun is hot. */
    private final double[] gunHeat = new double[EnemyTable.CAPACITY];
    private final int[] heatTurn = new int[EnemyTable.CAPACITY];

    private int prevSelfTurn = -1;
    private double prevSelfX;
    private double prevSelfY;
    private double prevSelfHeading;
    private double prevSelfSpeed;
    private int lastLateralDirection = 1;

    public EnemyFireSense(Blackboard bb) {
        this.waves = bb.enemyWavesSlot.claim(this);
    }

    @Override
    public void onRoundStart() {
        waves.clear();
        for (int i = 0; i < EnemyTable.CAPACITY; i++) {
            prevTurn[i] = -1;
            knownChange[i] = 0;
            gunHeat[i] = Rules.INITIAL_GUN_HEAT;
            heatTurn[i] = 0;
        }
        prevSelfTurn = -1;
        lastLateralDirection = 1;
    }

    @Override
    public void sense(TurnInput in, Blackboard bb) {
        EnemiesView enemies = bb.enemies();
        SelfView self = bb.self();
        int now = in.turn;
        double cooling = bb.game().gunCoolingRate;

        // Ram damage lands before this turn's scan (fact 4).
        for (int c = 0; c < in.collisionCount; c++) {
            int index = enemies.indexOf(in.collisionBotIds[c]);
            if (index != EnemiesView.NONE) {
                knownChange[index] -= Rules.RAM_DAMAGE;
            }
        }

        removeResolvedWaves(self, now);

        for (int i = 0; i < enemies.count(); i++) {
            EnemyView enemy = enemies.get(i);
            if (enemy.lastSeenTurn() != now) {
                continue;
            }
            // Cool through the turns not seen, then decide whether turn `now` could have fired.
            gunHeat[i] = Math.max(0, gunHeat[i] - cooling * Math.max(0, now - heatTurn[i] - 1));
            boolean gunReady = gunHeat[i] <= ENERGY_EPSILON;
            boolean fired = false;
            if (prevTurn[i] >= 0 && gunReady) {
                double drop = prevEnergy[i] - enemy.energy() + knownChange[i];
                if (hitWall(bb, enemy, prevSpeed[i])) {
                    // The impact speed is not observed; bots nearly always drive into a wall accelerating.
                    double impact = Math.min(Rules.MAX_SPEED, Math.abs(prevSpeed[i]) + Rules.ACCELERATION);
                    drop -= Rules.wallDamage(impact);
                }
                if (drop >= Rules.MIN_FIREPOWER - ENERGY_EPSILON && drop <= Rules.MAX_FIREPOWER + ENERGY_EPSILON) {
                    double power = Rules.clampFirepower(drop);
                    fired = true;
                    waves.firesDetected++;
                    gunHeat[i] = Rules.gunHeat(power);
                    if (prevTurn[i] == Rules.enemyFireDecisionTurn(now)) {
                        addWave(enemy.id(), i, power, self, now);
                    } else {
                        waves.unanchoredShots++;
                    }
                }
            }
            if (!fired) {
                gunHeat[i] = Math.max(0, gunHeat[i] - cooling);
            }
            heatTurn[i] = now;
            knownChange[i] = 0;
            prevTurn[i] = now;
            prevX[i] = enemy.x();
            prevY[i] = enemy.y();
            prevEnergy[i] = enemy.energy();
            prevSpeed[i] = enemy.speed();
        }

        // Bullet hits show one scan later (fact 4).
        for (int h = 0; h < in.bulletHitCount; h++) {
            BulletRecord hit = in.bulletHits[h];
            int index = enemies.indexOf(hit.victimId);
            if (index != EnemiesView.NONE) {
                knownChange[index] -= Rules.bulletDamage(hit.power);
            }
        }
        for (int h = 0; h < in.hitsTakenCount; h++) {
            BulletRecord hit = in.hitsTaken[h];
            int index = enemies.indexOf(hit.ownerId);
            if (index != EnemiesView.NONE) {
                knownChange[index] += Rules.energyGainOnHit(hit.power);
            }
            matchHit(hit, now);
        }

        prevSelfTurn = now;
        prevSelfX = self.x();
        prevSelfY = self.y();
        prevSelfHeading = self.heading();
        prevSelfSpeed = self.speed();
    }

    private void addWave(int shooterId, int index, double power, SelfView self, int now) {
        EnemyWave wave = waves.add();
        if (wave == null) {
            return;
        }
        int decided = Rules.enemyFireDecisionTurn(now);
        wave.shooterId = shooterId;
        wave.decisionTurn = decided;
        wave.originX = prevX[index];
        wave.originY = prevY[index];
        wave.power = power;
        wave.bulletSpeed = Rules.bulletSpeed(power);
        wave.maxEscapeAngle = Math.asin(Rules.MAX_SPEED / wave.bulletSpeed);
        // The shooter aimed at where this bot was at the decision turn; fall back to now if that turn was missed.
        boolean haveSelf = prevSelfTurn == decided;
        double selfX = haveSelf ? prevSelfX : self.x();
        double selfY = haveSelf ? prevSelfY : self.y();
        double selfHeading = haveSelf ? prevSelfHeading : self.heading();
        double selfSpeed = haveSelf ? prevSelfSpeed : self.speed();
        wave.directAngle = Geom.bearing(wave.originX, wave.originY, selfX, selfY);
        double lateral = selfSpeed * Math.sin(selfHeading - wave.directAngle);
        if (lateral > 0) {
            lastLateralDirection = 1;
        } else if (lateral < 0) {
            lastLateralDirection = -1;
        }
        wave.lateralDirection = lastLateralDirection;
    }

    /** Assigns a bullet that hit this bot to the wave of the same shooter and speed whose radius fits best. */
    private void matchHit(BulletRecord hit, int now) {
        waves.hitsTaken++;
        double speed = Rules.bulletSpeed(hit.power);
        int best = -1;
        double bestResidual = Double.MAX_VALUE;
        for (int w = 0; w < waves.count(); w++) {
            EnemyWave wave = waves.mutable(w);
            if (wave.shooterId != hit.ownerId || wave.hitTurn >= 0) {
                continue;
            }
            // Use the real bullet's speed: a shot fired while hitting a wall may have a misjudged power.
            double residual = Geom.distance(wave.originX, wave.originY, hit.x, hit.y)
                    - Rules.bulletDistance(wave.decisionTurn, now, speed);
            if (Math.abs(residual) < Math.abs(bestResidual)) {
                best = w;
                bestResidual = residual;
            }
        }
        if (best >= 0 && Math.abs(bestResidual) <= 1.5 * speed) {
            EnemyWave wave = waves.mutable(best);
            if (Math.abs(wave.bulletSpeed - speed) > ENERGY_EPSILON) {
                wave.power = hit.power;
                wave.bulletSpeed = speed;
                wave.maxEscapeAngle = Math.asin(Rules.MAX_SPEED / speed);
                waves.powerCorrected++;
            }
            wave.hitTurn = now;
            wave.hitX = hit.x;
            wave.hitY = hit.y;
            wave.hitResidual = bestResidual;
            waves.hitsOnWave++;
        }
    }

    /** Drops waves that hit this bot on an earlier turn, and waves whose bullet is now past this bot. */
    private void removeResolvedWaves(SelfView self, int now) {
        for (int w = waves.count() - 1; w >= 0; w--) {
            EnemyWave wave = waves.mutable(w);
            boolean hitEarlier = wave.hitTurn >= 0 && wave.hitTurn < now;
            double distance = Geom.distance(wave.originX, wave.originY, self.x(), self.y());
            boolean passed = wave.radius(now) > distance + 2 * Rules.BOT_HIT_RADIUS;
            if (hitEarlier || passed) {
                waves.remove(w);
            }
        }
    }

    /**
     * Whether the enemy most likely hit a wall this turn: it stands still, clamped against a wall, after moving.
     * Wall damage only applies on first contact, so an enemy already stopped at the wall took none.
     */
    private static boolean hitWall(Blackboard bb, EnemyView enemy, double previousSpeed) {
        if (enemy.speed() != 0 || previousSpeed == 0) {
            return false;
        }
        double min = Rules.BOT_HIT_RADIUS + WALL_EPSILON;
        double maxX = bb.game().arenaWidth - min;
        double maxY = bb.game().arenaHeight - min;
        return enemy.x() <= min || enemy.y() <= min || enemy.x() >= maxX || enemy.y() >= maxY;
    }

    @Override
    public String toString() {
        return "EnemyFireSense";
    }
}
