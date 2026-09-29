package fnl.bots.retcon.gun;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.roles.AimModel;
import fnl.bots.framework.rules.Rules;

/**
 * Aims where the target will be if it keeps its heading and speed, stopping it at the walls. It steps the target
 * forward one turn at a time until the bullet, v·k out after k turns (physics fact 2), reaches the predicted
 * position, and aims there from this bot's current position, which is where the bullet starts (fact 1).
 * <p>
 * Book: <a href="https://book.robocode.dev/targeting/simple-targeting/linear-targeting.html">Linear Targeting</a>.
 */
public final class LinearAimModel implements AimModel {

    private static final int MAX_TURNS = 150;

    @Override
    public double aim(Blackboard bb, EnemyView target, double bulletSpeed) {
        double originX = bb.self().x();
        double originY = bb.self().y();
        double min = Rules.BOT_HIT_RADIUS;
        double maxX = bb.game().arenaWidth - min;
        double maxY = bb.game().arenaHeight - min;
        double dx = Math.cos(target.heading()) * target.speed();
        double dy = Math.sin(target.heading()) * target.speed();
        double x = target.x();
        double y = target.y();
        int decided = bb.turn().turn();
        for (int k = 1; k <= MAX_TURNS; k++) {
            x = Geom.clamp(x + dx, min, maxX);
            y = Geom.clamp(y + dy, min, maxY);
            if (Rules.bulletDistance(decided, decided + k, bulletSpeed) >= Geom.distance(originX, originY, x, y)) {
                break;
            }
        }
        return Geom.bearing(originX, originY, x, y);
    }
}
