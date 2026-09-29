package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.pipeline.Stage;
import fnl.bots.framework.roles.Gun;

/** Model: runs the collector of every gun, whatever the active mode, so no targeting data is lost. */
public final class CollectStage implements Stage {

    private final Gun[] guns;

    public CollectStage(Blackboard bb, Strategies strategies) {
        this.guns = strategies.distinctGuns();
        for (Gun gun : guns) {
            gun.collector().wire(bb);
        }
    }

    @Override
    public void onRoundStart() {
        for (Gun gun : guns) {
            gun.collector().onRoundStart();
        }
    }

    @Override
    public void run(Blackboard bb) {
        for (Gun gun : guns) {
            gun.collector().collect(bb);
        }
    }

    @Override
    public String toString() {
        return "CollectStage";
    }
}
