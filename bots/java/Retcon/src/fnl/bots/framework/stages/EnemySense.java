package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemiesView;
import fnl.bots.framework.blackboard.EnemyState;
import fnl.bots.framework.blackboard.EnemyTable;
import fnl.bots.framework.blackboard.SelfView;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.pipeline.SenseStage;
import fnl.bots.framework.sense.ScanRecord;
import fnl.bots.framework.sense.TurnInput;

/** Sense: tracks enemies from scans and deaths. Must run after {@link SelfSense}, which it reads. */
public final class EnemySense implements SenseStage {

    private final EnemyTable enemies;

    public EnemySense(Blackboard bb) {
        this.enemies = bb.enemiesSlot.claim(this);
    }

    @Override
    public void onRoundStart() {
        enemies.clear();
    }

    @Override
    public void sense(TurnInput in, Blackboard bb) {
        SelfView self = bb.self();
        for (int i = 0; i < in.scanCount; i++) {
            ScanRecord scan = in.scans[i];
            if (bb.game().isTeammate(scan.botId)) {
                continue;
            }
            EnemyState enemy = enemies.getOrAdd(scan.botId);
            if (enemy == null) {
                continue;
            }
            enemy.energyDrop = enemy.lastSeenTurn >= 0 ? enemy.energy - scan.energy : 0;
            enemy.alive = true;
            enemy.lastSeenTurn = in.turn;
            enemy.x = scan.x;
            enemy.y = scan.y;
            enemy.heading = scan.heading;
            enemy.speed = scan.speed;
            enemy.energy = scan.energy;
            enemy.distance = Geom.distance(self.x(), self.y(), scan.x, scan.y);
            enemy.bearing = Geom.bearing(self.x(), self.y(), scan.x, scan.y);
        }
        for (int d = 0; d < in.deadCount; d++) {
            int index = enemies.indexOf(in.deadBotIds[d]);
            if (index != EnemiesView.NONE) {
                enemies.mutable(index).alive = false;
            }
        }
        enemies.reportedAlive = in.enemyCount;
    }

    @Override
    public String toString() {
        return "EnemySense";
    }
}
