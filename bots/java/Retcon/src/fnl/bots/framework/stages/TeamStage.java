package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.pipeline.Stage;
import fnl.bots.framework.team.TeamInbox;
import fnl.bots.framework.team.TeamOutbox;

/** Decide: lets the active team comms read this turn's messages and queue new ones. */
public final class TeamStage implements Stage {

    private final Strategies strategies;
    private final TeamInbox inbox;
    private final TeamOutbox outbox;

    public TeamStage(Strategies strategies, TeamInbox inbox, TeamOutbox outbox) {
        this.strategies = strategies;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    @Override
    public void run(Blackboard bb) {
        var comms = strategies.active(bb).comms();
        comms.receive(bb, inbox);
        comms.send(bb, outbox);
    }

    @Override
    public String toString() {
        return "TeamStage";
    }
}
