package fnl.bots.retcon.firepower;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.roles.FirePower;
import fnl.bots.framework.rules.Rules;

/**
 * The book's hybrid formula from distance and own energy (no hit-rate factor yet), capped at the least power that
 * finishes the target.
 * <p>
 * Book: <a href="https://book.robocode.dev/energy-and-scoring/bullet-power-selection-strategy.html">Bullet Power
 * Selection Strategy</a> (hybrid formula, finishing shots) and
 * <a href="https://book.robocode.dev/energy-and-scoring/energy-as-a-resource.html">Energy as a Resource</a>.
 */
public final class BasicFirePower implements FirePower {

    private static final double BASE_POWER = 2.0;
    private static final double CLOSE = 150;
    private static final double FAR = 400;
    private static final double CRITICAL_ENERGY = 15;
    private static final double LOW_ENERGY = 35;

    @Override
    public double power(Blackboard bb, EnemyView target) {
        double distance = Geom.distance(bb.self().x(), bb.self().y(), target.x(), target.y());
        double energy = bb.self().energy();
        double distanceFactor = distance < CLOSE ? 1.5 : distance < FAR ? 1.0 : 0.6;
        double energyFactor = energy < CRITICAL_ENERGY ? 0.3 : energy < LOW_ENERGY ? 0.7 : 1.0;
        double power = Rules.clampFirepower(BASE_POWER * distanceFactor * energyFactor);
        return Math.min(power, Rules.firepowerForDamage(target.energy()));
    }
}
