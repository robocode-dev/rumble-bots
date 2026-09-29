package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.ModeState;
import fnl.bots.framework.pipeline.Stage;
import fnl.bots.framework.roles.Mode;

/** Model: selects the mode from how many enemies and teammates are alive. */
public final class ModeStage implements Stage {

    private final ModeState mode;
    private final Mode selector;

    public ModeStage(Blackboard bb, Mode selector) {
        this.mode = bb.modeSlot.claim(this);
        this.selector = selector;
    }

    @Override
    public void run(Blackboard bb) {
        mode.enemiesAlive = bb.enemies().reportedAliveCount();
        mode.teammatesAlive = bb.self().teammatesAlive();
        mode.kind = selector.select(mode.enemiesAlive, mode.teammatesAlive);
    }

    @Override
    public String toString() {
        return "ModeStage";
    }
}
