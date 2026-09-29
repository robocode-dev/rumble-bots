package fnl.bots.retcon.target;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemiesView;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.roles.TargetSelector;

/**
 * Targets the closest living enemy that has been scanned, measured from this bot's current position to the
 * enemy's last known position.
 * <p>
 * Book: <a href="https://book.robocode.dev/melee-combat/melee-strategy.html">Melee Strategy</a>.
 */
public final class ClosestTargetSelector implements TargetSelector {

    @Override
    public int select(Blackboard bb) {
        EnemiesView enemies = bb.enemies();
        double x = bb.self().x();
        double y = bb.self().y();
        int best = EnemiesView.NONE;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int i = 0; i < enemies.count(); i++) {
            EnemyView enemy = enemies.get(i);
            if (!enemy.alive() || enemy.lastSeenTurn() < 0) {
                continue;
            }
            double d = Geom.distanceSq(x, y, enemy.x(), enemy.y());
            if (d < bestDistanceSq) {
                bestDistanceSq = d;
                best = i;
            }
        }
        return best;
    }
}
