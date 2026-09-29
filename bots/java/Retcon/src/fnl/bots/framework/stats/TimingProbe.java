package fnl.bots.framework.stats;

import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.rules.Rules;
import fnl.bots.framework.sense.BulletRecord;
import fnl.bots.framework.sense.ScanRecord;
import fnl.bots.framework.sense.TurnInput;

import java.util.Arrays;

/**
 * Checks the {@link Rules} turn-order facts against the live server:
 * <ol>
 * <li>a bullet requested after turn t is created in turn t + 1 ({@link Rules#bulletCreatedTurn}),</li>
 * <li>from the position observed at t, along the gun heading observed at t
 * ({@link Rules#FIRE_USES_PRE_TURN_GUN_HEADING}),</li>
 * <li>and hits a still target in the turn {@link Rules#firstHitTurnOnStillTarget} predicts.</li>
 * </ol>
 * Allocation-free: shots are kept in a small fixed ring.
 */
public final class TimingProbe {

    private static final int RING = 64;
    private static final double POSITION_EPSILON = 1e-6;
    private static final double ANGLE_EPSILON = 1e-6;

    private final int[] bulletIds = new int[RING];
    private final int[] decidedAt = new int[RING];
    private final double[] originX = new double[RING];
    private final double[] originY = new double[RING];
    private final double[] heading = new double[RING];
    private final double[] speed = new double[RING];
    private final double[] targetX = new double[RING];
    private final double[] targetY = new double[RING];
    private int next;
    /** Bullet ids restart every round, so the ring is cleared when the round changes. */
    private int round = -1;

    private boolean pending;
    private int pendingTurn;
    private double pendingX;
    private double pendingY;
    private double pendingHeading;
    private double pendingTargetX;
    private double pendingTargetY;

    /** All bullets this bot fired and all its bullets that hit a bot, for the real hit rate. */
    public long bulletsFired;
    public long bulletHits;
    public long firesChecked;
    public long firesNotCreated;
    public long createdTurnMatches;
    /** Fired event position equals the position observed when the shot was decided. */
    public long originMatches;
    /** Fired event position equals that origin plus one bullet step (reported after the first move). */
    public long originMatchesAfterFirstMove;
    public long headingMatches;
    public long stillHitsChecked;
    public long stillHitMatches;
    /** Hits whose victim had moved since the shot was decided; their hit turn is not predicted. */
    public long hitsOnMovingTargets;
    public final StringBuilder mismatches = new StringBuilder();
    private int mismatchCount;

    /**
     * The bot requested a shot in response to {@code turn}, from the given position and gun heading, at a target
     * last seen at ({@code targetX}, {@code targetY}).
     */
    public void onFireRequested(int turn, double x, double y, double gunHeading, double targetX, double targetY) {
        pending = true;
        pendingTurn = turn;
        pendingX = x;
        pendingY = y;
        pendingHeading = gunHeading;
        pendingTargetX = targetX;
        pendingTargetY = targetY;
    }

    /** Feeds this turn's bullet events. Call after the adapter has filled the input. */
    public void observe(TurnInput in) {
        if (in.round != round) {
            round = in.round;
            Arrays.fill(bulletIds, -1);
        }
        bulletsFired += in.bulletsFiredCount;
        bulletHits += in.bulletHitCount;
        for (int i = 0; i < in.bulletsFiredCount; i++) {
            onBulletFired(in.bulletsFired[i]);
        }
        if (pending && in.turn > pendingTurn) {
            // Requested, but no bullet appeared in the next turn (e.g. a skipped turn or changed energy).
            pending = false;
            firesNotCreated++;
        }
        for (int i = 0; i < in.bulletHitCount; i++) {
            onBulletHit(in, in.bulletHits[i]);
        }
    }

    private void onBulletFired(BulletRecord fired) {
        if (!pending) {
            return;
        }
        pending = false;
        firesChecked++;
        if (fired.turn == Rules.bulletCreatedTurn(pendingTurn)) {
            createdTurnMatches++;
        } else {
            mismatch("created turn " + fired.turn + " != " + Rules.bulletCreatedTurn(pendingTurn));
        }
        double v = Rules.bulletSpeed(fired.power);
        if (near(fired.x, pendingX) && near(fired.y, pendingY)) {
            originMatches++;
        } else if (near(fired.x - Math.cos(fired.heading) * v, pendingX)
                && near(fired.y - Math.sin(fired.heading) * v, pendingY)) {
            originMatchesAfterFirstMove++;
        } else {
            mismatch("origin (" + fired.x + "," + fired.y + ") vs observed (" + pendingX + "," + pendingY + ")");
        }
        if (Math.abs(Angles.normalizeRelative(fired.heading - pendingHeading)) < ANGLE_EPSILON) {
            headingMatches++;
        } else {
            mismatch("heading " + fired.heading + " vs observed gun heading " + pendingHeading);
        }
        int slot = next;
        next = (next + 1) % RING;
        bulletIds[slot] = fired.bulletId;
        decidedAt[slot] = pendingTurn;
        originX[slot] = pendingX;
        originY[slot] = pendingY;
        heading[slot] = fired.heading;
        speed[slot] = v;
        targetX[slot] = pendingTargetX;
        targetY[slot] = pendingTargetY;
    }

    private void onBulletHit(TurnInput in, BulletRecord hit) {
        int slot = find(hit.bulletId);
        if (slot < 0) {
            return;
        }
        ScanRecord victim = null;
        for (int i = 0; i < in.scanCount; i++) {
            if (in.scans[i].botId == hit.victimId) {
                victim = in.scans[i];
            }
        }
        if (victim == null || victim.speed != 0 || !near(victim.x, targetX[slot]) || !near(victim.y, targetY[slot])) {
            hitsOnMovingTargets++;
            return;
        }
        stillHitsChecked++;
        int predicted = Rules.firstHitTurnOnStillTarget(decidedAt[slot], originX[slot], originY[slot], heading[slot],
                victim.x, victim.y, speed[slot]);
        if (predicted == hit.turn) {
            stillHitMatches++;
        } else {
            mismatch("hit turn " + hit.turn + " != predicted " + predicted + " (d="
                    + Geom.distance(originX[slot], originY[slot], victim.x, victim.y) + ", v=" + speed[slot] + ")");
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < POSITION_EPSILON;
    }

    private int find(int bulletId) {
        for (int i = 0; i < RING; i++) {
            if (bulletIds[i] == bulletId) {
                return i;
            }
        }
        return -1;
    }

    private void mismatch(String text) {
        if (mismatchCount++ < 10) {
            mismatches.append(text).append("; ");
        }
    }
}
