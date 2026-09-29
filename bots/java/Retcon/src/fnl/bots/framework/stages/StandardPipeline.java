package fnl.bots.framework.stages;

import fnl.bots.framework.arbitration.Arbiter;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.ModeKind;
import fnl.bots.framework.pipeline.Pipeline;
import fnl.bots.framework.roles.Mode;
import fnl.bots.framework.roles.StrategySet;
import fnl.bots.framework.sense.GameInfo;
import fnl.bots.framework.sense.TurnInput;
import fnl.bots.framework.team.TeamInbox;
import fnl.bots.framework.team.TeamOutbox;

import java.util.Map;

/**
 * Wires the standard pipeline:
 * <ul>
 * <li>Sense: {@link SelfSense}, {@link EnemySense}, {@link EnemyFireSense}</li>
 * <li>Model: {@link ModeStage}, {@link CollectStage}</li>
 * <li>Decide: {@link TargetStage}, {@link TeamStage}, {@link MovementStage}, {@link RadarStage}, {@link GunStage}</li>
 * <li>Arbitrate: the {@link Arbiter}</li>
 * </ul>
 */
public final class StandardPipeline {

    /** Default share of the server turn timeout a bot allows itself for compute (5 ms of a 30 ms timeout). */
    public static final double DEFAULT_BUDGET_SHARE = 1.0 / 6;

    private StandardPipeline() {
    }

    public static Pipeline create(GameInfo game, TurnInput input, Mode mode, Map<ModeKind, StrategySet> sets,
                                  double budgetShare) {
        Blackboard bb = new Blackboard(game);
        Arbiter arbiter = new Arbiter();
        TeamOutbox outbox = new TeamOutbox();
        Strategies strategies = new Strategies(sets);
        return Pipeline.builder(bb, arbiter, outbox)
                .sense(new SelfSense(bb, budgetShare))
                .sense(new EnemySense(bb))
                .sense(new EnemyFireSense(bb))
                .model(new ModeStage(bb, mode))
                .model(new CollectStage(bb, strategies))
                .decide(new TargetStage(bb, strategies))
                .decide(new TeamStage(strategies, new TeamInbox(input), outbox))
                .decide(new MovementStage(arbiter, strategies))
                .decide(new RadarStage(arbiter, strategies))
                .decide(new GunStage(arbiter, strategies))
                .build();
    }
}
