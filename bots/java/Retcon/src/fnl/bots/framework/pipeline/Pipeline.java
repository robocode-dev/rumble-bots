package fnl.bots.framework.pipeline;

import fnl.bots.framework.arbitration.Arbiter;
import fnl.bots.framework.arbitration.Intent;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.sense.TurnInput;
import fnl.bots.framework.team.TeamOutbox;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-turn pipeline: Sense → Model → Decide → Arbitrate. Stages run in the order they were added within their
 * phase. {@link #step} allocates nothing.
 */
public final class Pipeline {

    private final Blackboard bb;
    private final Arbiter arbiter;
    private final SenseStage[] sense;
    private final Stage[] model;
    private final Stage[] decide;
    private final Intent intent = new Intent();
    private final TeamOutbox outbox;

    private Pipeline(Builder builder) {
        this.bb = builder.bb;
        this.arbiter = builder.arbiter;
        this.outbox = builder.outbox;
        this.sense = builder.sense.toArray(new SenseStage[0]);
        this.model = builder.model.toArray(new Stage[0]);
        this.decide = builder.decide.toArray(new Stage[0]);
    }

    public static Builder builder(Blackboard bb, Arbiter arbiter, TeamOutbox outbox) {
        return new Builder(bb, arbiter, outbox);
    }

    public void onRoundStart() {
        for (SenseStage stage : sense) {
            stage.onRoundStart();
        }
        for (Stage stage : model) {
            stage.onRoundStart();
        }
        for (Stage stage : decide) {
            stage.onRoundStart();
        }
    }

    /** Runs one turn and returns the resolved intent (the same instance every turn). */
    public Intent step(TurnInput in) {
        for (SenseStage stage : sense) {
            stage.sense(in, bb);
        }
        for (Stage stage : model) {
            stage.run(bb);
        }
        arbiter.beginTurn();
        for (Stage stage : decide) {
            stage.run(bb);
        }
        arbiter.resolve(bb.self().speed(), intent);
        return intent;
    }

    public Blackboard blackboard() {
        return bb;
    }

    public Intent intent() {
        return intent;
    }

    /** Team messages queued this turn; the adapter sends and then clears them. */
    public TeamOutbox outbox() {
        return outbox;
    }

    /** Wires stages into phases. Stages claim their slots and actuators in their constructors. */
    public static final class Builder {

        private final Blackboard bb;
        private final Arbiter arbiter;
        private final TeamOutbox outbox;
        private final List<SenseStage> sense = new ArrayList<>();
        private final List<Stage> model = new ArrayList<>();
        private final List<Stage> decide = new ArrayList<>();

        private Builder(Blackboard bb, Arbiter arbiter, TeamOutbox outbox) {
            this.bb = bb;
            this.arbiter = arbiter;
            this.outbox = outbox;
        }

        public Builder sense(SenseStage stage) {
            sense.add(stage);
            return this;
        }

        public Builder model(Stage stage) {
            model.add(stage);
            return this;
        }

        public Builder decide(Stage stage) {
            decide.add(stage);
            return this;
        }

        public Pipeline build() {
            return new Pipeline(this);
        }
    }
}
