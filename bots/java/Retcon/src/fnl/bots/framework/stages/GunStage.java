package fnl.bots.framework.stages;

import fnl.bots.framework.arbitration.Arbiter;
import fnl.bots.framework.arbitration.FireCommand;
import fnl.bots.framework.arbitration.GunCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;
import fnl.bots.framework.pipeline.Stage;
import fnl.bots.framework.roles.StrategySet;

/** Decide: the owner of the GUN and FIRE actuators; delegates to the active gun and firepower. */
public final class GunStage implements Stage {

    private final GunCommand gun;
    private final FireCommand fire;
    private final Strategies strategies;

    public GunStage(Arbiter arbiter, Strategies strategies) {
        this.gun = arbiter.claimGun(this);
        this.fire = arbiter.claimFire(this);
        this.strategies = strategies;
    }

    @Override
    public void run(Blackboard bb) {
        EnemyView target = bb.targetEnemy();
        if (target == null) {
            return;
        }
        StrategySet set = strategies.active(bb);
        set.gun().decide(bb, target, set.power().power(bb, target), gun, fire);
    }

    @Override
    public String toString() {
        return "GunStage";
    }
}
