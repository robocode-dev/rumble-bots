package fnl.bots.framework.stages;

import fnl.bots.framework.arbitration.Arbiter;
import fnl.bots.framework.arbitration.BodyCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.pipeline.Stage;

/** Decide: the owner of the BODY actuator; delegates to the active movement. */
public final class MovementStage implements Stage {

    private final BodyCommand body;
    private final Strategies strategies;

    public MovementStage(Arbiter arbiter, Strategies strategies) {
        this.body = arbiter.claimBody(this);
        this.strategies = strategies;
    }

    @Override
    public void run(Blackboard bb) {
        strategies.active(bb).movement().decide(bb, body);
    }

    @Override
    public String toString() {
        return "MovementStage";
    }
}
