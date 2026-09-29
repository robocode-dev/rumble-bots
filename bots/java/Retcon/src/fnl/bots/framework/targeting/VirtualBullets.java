package fnl.bots.framework.targeting;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemiesView;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.blackboard.GunStats;
import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.roles.AimModel;
import fnl.bots.framework.roles.Collector;
import fnl.bots.framework.rules.Rules;

import java.util.Arrays;

/**
 * Virtual guns: every real shot also fires one virtual bullet per aim model, along that model's aim. When the shot's
 * wave reaches the target, each virtual bullet counts as a hit if the target is inside its hit angle
 * ({@link Rules#hitHalfAngle}). The totals go to the blackboard's {@code gunStats}, where an aim selector can pick
 * the best model. The wave starts at this bot's position at the decision turn and is v·(now − t) out at turn
 * {@code now} (physics facts 1 and 2).
 * <p>
 * Implements <a href="https://book.robocode.dev/targeting/simple-targeting/virtual-guns-mean-targeting.html">Virtual
 * Guns</a>. Stats are kept for the whole game; waves in flight are dropped at a new round. Allocation-free once
 * wired. Only one virtual-gun collector per pipeline can claim {@code gunStats}.
 */
public final class VirtualBullets implements Collector {

    private static final int CAPACITY = 32;

    private GunStats stats;
    private int models;
    /** Model names, known from the gun before the pipeline is wired; published to the stats once claimed. */
    private String[] pendingNames;
    private final boolean[] active = new boolean[CAPACITY];
    private final int[] decisionTurn = new int[CAPACITY];
    private final int[] targetId = new int[CAPACITY];
    private final double[] originX = new double[CAPACITY];
    private final double[] originY = new double[CAPACITY];
    private final double[] speed = new double[CAPACITY];
    private final double[][] aims = new double[CAPACITY][GunStats.MAX_MODELS];
    private int next;

    @Override
    public void wire(Blackboard bb) {
        stats = bb.gunStatsSlot.claim(this);
        publishModels();
    }

    @Override
    public void onModels(AimModel[] aimModels) {
        if (aimModels.length > GunStats.MAX_MODELS) {
            throw new IllegalArgumentException("At most " + GunStats.MAX_MODELS + " aim models");
        }
        models = aimModels.length;
        pendingNames = new String[models];
        for (int i = 0; i < models; i++) {
            pendingNames[i] = aimModels[i].getClass().getSimpleName();
        }
        publishModels();
    }

    private void publishModels() {
        if (stats != null && pendingNames != null) {
            stats.models = models;
            System.arraycopy(pendingNames, 0, stats.names, 0, models);
        }
    }

    @Override
    public void onRoundStart() {
        Arrays.fill(active, false);
    }

    @Override
    public void onFire(Blackboard bb, EnemyView target, double bulletSpeed, double[] modelAims) {
        int slot = next;
        next = (next + 1) % CAPACITY;
        active[slot] = true;
        decisionTurn[slot] = bb.turn().turn();
        targetId[slot] = target.id();
        originX[slot] = bb.self().x();
        originY[slot] = bb.self().y();
        speed[slot] = bulletSpeed;
        System.arraycopy(modelAims, 0, aims[slot], 0, models);
    }

    @Override
    public void collect(Blackboard bb) {
        if (stats == null) {
            return;
        }
        int now = bb.turn().turn();
        EnemiesView enemies = bb.enemies();
        for (int w = 0; w < CAPACITY; w++) {
            if (!active[w]) {
                continue;
            }
            int index = enemies.indexOf(targetId[w]);
            EnemyView target = index == EnemiesView.NONE ? null : enemies.get(index);
            if (target == null || !target.alive()) {
                active[w] = false;
                continue;
            }
            if (target.lastSeenTurn() != now) {
                continue;
            }
            double distance = Geom.distance(originX[w], originY[w], target.x(), target.y());
            if (Rules.bulletDistance(decisionTurn[w], now, speed[w]) < distance) {
                continue;
            }
            active[w] = false;
            double bearing = Geom.bearing(originX[w], originY[w], target.x(), target.y());
            double tolerance = Rules.hitHalfAngle(distance);
            for (int m = 0; m < models; m++) {
                double aim = aims[w][m];
                if (Double.isNaN(aim)) {
                    continue;
                }
                stats.shots[m]++;
                if (Math.abs(Angles.normalizeRelative(aim - bearing)) <= tolerance) {
                    stats.hits[m]++;
                }
            }
        }
    }
}
