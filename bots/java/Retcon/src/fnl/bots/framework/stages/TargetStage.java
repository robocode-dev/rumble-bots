package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.TargetState;
import fnl.bots.framework.pipeline.Stage;

/** Decide: selects the target with the active strategy set's target selector. Runs first in Decide. */
public final class TargetStage implements Stage {

    private final TargetState target;
    private final Strategies strategies;

    public TargetStage(Blackboard bb, Strategies strategies) {
        this.target = bb.targetSlot.claim(this);
        this.strategies = strategies;
    }

    @Override
    public void run(Blackboard bb) {
        target.enemyIndex = strategies.active(bb).target().select(bb);
    }

    @Override
    public String toString() {
        return "TargetStage";
    }
}
