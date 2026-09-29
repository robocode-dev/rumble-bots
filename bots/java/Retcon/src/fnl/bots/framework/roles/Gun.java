package fnl.bots.framework.roles;

import fnl.bots.framework.arbitration.FireCommand;
import fnl.bots.framework.arbitration.GunCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.blackboard.SelfView;
import fnl.bots.framework.geom.Angles;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.rules.Rules;

/**
 * A gun is a {@link Collector}, one or more {@link AimModel}s and an {@link AimSelector} that picks among them.
 * <p>
 * It turns the gun toward the chosen aim angle and fires only when the <em>current</em> gun heading is already on
 * target: the server fires before it applies the same intent's gun rotation, so the bullet leaves along the heading
 * observed this turn ({@link Rules#FIRE_USES_PRE_TURN_GUN_HEADING}).
 */
public final class Gun {

    private final Collector collector;
    private final AimModel[] models;
    private final AimSelector selector;
    private final double[] aims;

    public Gun(Collector collector, AimModel[] models, AimSelector selector) {
        if (models.length == 0) {
            throw new IllegalArgumentException("A gun needs at least one aim model");
        }
        this.collector = collector;
        this.models = models.clone();
        this.selector = selector;
        this.aims = new double[models.length];
        collector.onModels(this.models);
    }

    public Collector collector() {
        return collector;
    }

    /**
     * Aims at {@code target} and fires with {@code firepower} when on target.
     *
     * @param target the target, or null to leave the gun idle
     */
    public void decide(Blackboard bb, EnemyView target, double firepower, GunCommand gun, FireCommand fire) {
        if (target == null) {
            return;
        }
        double power = firepower >= Rules.MIN_FIREPOWER ? firepower : Rules.MIN_FIREPOWER;
        AimModel model = models[selector.choose(bb, models)];
        double aim = model.aim(bb, target, Rules.bulletSpeed(power));
        if (Double.isNaN(aim)) {
            return;
        }
        SelfView self = bb.self();
        double offset = Angles.normalizeRelative(aim - self.gunHeading());
        gun.turnRate = offset;

        double distance = Geom.distance(self.x(), self.y(), target.x(), target.y());
        boolean onTarget = Math.abs(offset) <= Rules.hitHalfAngle(distance);
        if (onTarget && firepower >= Rules.MIN_FIREPOWER && Rules.canFire(self.gunHeat(), self.energy(), firepower)) {
            fire.firepower = firepower;
            double speed = Rules.bulletSpeed(firepower);
            for (int i = 0; i < models.length; i++) {
                aims[i] = models[i].aim(bb, target, speed);
            }
            collector.onFire(bb, target, speed, aims);
        }
    }
}
