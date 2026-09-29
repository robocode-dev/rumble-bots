package fnl.bots.retcon.radar;

import fnl.bots.framework.arbitration.RadarCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.blackboard.SelfView;
import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.roles.Radar;
import fnl.bots.framework.rules.Rules;

/**
 * Width Lock: turns the radar to the target's bearing and past it by the target's half-width at that distance, so
 * the beam sweeps over the target again next turn. Tank Royale scans post-move positions, so the overshoot also
 * covers one turn of the target's movement. Without a fresh scan it spins to reacquire.
 * <p>
 * Book: <a href="https://book.robocode.dev/radar/one-on-one-radar/perfect-locks.html">Perfect Locks</a>
 * (Width Lock).
 */
public final class PerfectLockRadar implements Radar {

    private double spinDirection = 1;

    @Override
    public void decide(Blackboard bb, RadarCommand out) {
        EnemyView target = bb.targetEnemy();
        if (target == null || target.lastSeenTurn() < bb.turn().turn() - 1) {
            out.turnRate = spinDirection * Rules.MAX_RADAR_TURN_RATE;
            return;
        }
        SelfView self = bb.self();
        double bearing = Geom.bearing(self.x(), self.y(), target.x(), target.y());
        double distance = Geom.distance(self.x(), self.y(), target.x(), target.y());
        double needed = Angles.normalizeRelative(bearing - self.radarHeading());
        double overshoot = Math.atan((Rules.BOT_HIT_RADIUS + Rules.MAX_SPEED) / Math.max(distance, 1));
        double direction = needed >= 0 ? 1 : -1;
        out.turnRate = needed + direction * overshoot;
        spinDirection = direction;
    }
}
