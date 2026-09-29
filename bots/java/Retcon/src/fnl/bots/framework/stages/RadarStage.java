package fnl.bots.framework.stages;

import fnl.bots.framework.arbitration.Arbiter;
import fnl.bots.framework.arbitration.RadarCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.pipeline.Stage;

/** Decide: the owner of the RADAR actuator; delegates to the active radar. */
public final class RadarStage implements Stage {

    private final RadarCommand radar;
    private final Strategies strategies;

    public RadarStage(Arbiter arbiter, Strategies strategies) {
        this.radar = arbiter.claimRadar(this);
        this.strategies = strategies;
    }

    @Override
    public void run(Blackboard bb) {
        strategies.active(bb).radar().decide(bb, radar);
    }

    @Override
    public String toString() {
        return "RadarStage";
    }
}
