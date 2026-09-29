package fnl.bots.retcon.gun;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.roles.AimModel;

/**
 * Aims straight at the target's last scanned position.
 * <p>
 * Book: <a href="https://book.robocode.dev/targeting/simple-targeting/head-on-targeting.html">Head-On
 * Targeting</a>.
 */
public final class HeadOnAimModel implements AimModel {

    @Override
    public double aim(Blackboard bb, EnemyView target, double bulletSpeed) {
        return Geom.bearing(bb.self().x(), bb.self().y(), target.x(), target.y());
    }
}
