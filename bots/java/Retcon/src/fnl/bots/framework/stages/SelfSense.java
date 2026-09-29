package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.SelfState;
import fnl.bots.framework.blackboard.TurnState;
import fnl.bots.framework.pipeline.SenseStage;
import fnl.bots.framework.sense.GameInfo;
import fnl.bots.framework.sense.TurnInput;

/** Sense: writes the turn clock, the compute budget and this bot's own state. */
public final class SelfSense implements SenseStage {

    private final TurnState turn;
    private final SelfState self;
    private final int[] teammateIds;
    private final boolean[] teammateDead;
    private final double budgetShare;

    /**
     * @param budgetShare share of the server turn timeout this bot allows itself for compute, in (0, 1]
     */
    public SelfSense(Blackboard bb, double budgetShare) {
        if (!(budgetShare > 0 && budgetShare <= 1)) {
            throw new IllegalArgumentException("budgetShare must be in (0, 1]: " + budgetShare);
        }
        this.turn = bb.turnSlot.claim(this);
        this.self = bb.selfSlot.claim(this);
        GameInfo game = bb.game();
        this.teammateIds = game.teammateIds.clone();
        this.teammateDead = new boolean[teammateIds.length];
        this.budgetShare = budgetShare;
        turn.turnTimeoutMicros = game.turnTimeoutMicros;
        turn.computeBudgetMicros = (int) (game.turnTimeoutMicros * budgetShare);
    }

    @Override
    public void onRoundStart() {
        for (int i = 0; i < teammateDead.length; i++) {
            teammateDead[i] = false;
        }
        self.teammatesAlive = teammateIds.length;
    }

    @Override
    public void sense(TurnInput in, Blackboard bb) {
        turn.turnStartNanos = System.nanoTime();
        turn.round = in.round;
        turn.turn = in.turn;
        turn.turnTimeoutMicros = bb.game().turnTimeoutMicros;
        turn.computeBudgetMicros = (int) (turn.turnTimeoutMicros * budgetShare);

        self.x = in.x;
        self.y = in.y;
        self.heading = in.heading;
        self.gunHeading = in.gunHeading;
        self.radarHeading = in.radarHeading;
        self.speed = in.speed;
        self.energy = in.energy;
        self.gunHeat = in.gunHeat;

        for (int d = 0; d < in.deadCount; d++) {
            int deadId = in.deadBotIds[d];
            for (int i = 0; i < teammateIds.length; i++) {
                if (teammateIds[i] == deadId && !teammateDead[i]) {
                    teammateDead[i] = true;
                    self.teammatesAlive--;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "SelfSense";
    }
}
