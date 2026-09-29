package fnl.bots.framework.roles;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;

/**
 * Collects targeting data in the Model phase, every turn, whichever mode is active. Waves, GuessFactor statistics
 * and the Retroactive Hit Analysis history buffer plug in here.
 */
public interface Collector {

    void collect(Blackboard bb);

    /** Called when a new round starts. */
    default void onRoundStart() {
    }

    /** Called once while the pipeline is wired, so a collector can claim the blackboard slot it writes. */
    default void wire(Blackboard bb) {
    }

    /** Called once by the {@link Gun} with its aim models, in the order its selector indexes them. */
    default void onModels(AimModel[] models) {
    }

    /**
     * Called when the gun fires at {@code target}: {@code aims[i]} is model i's absolute aim angle for this shot
     * ({@link Double#NaN} when it had no opinion). The array is reused; copy what you keep.
     */
    default void onFire(Blackboard bb, EnemyView target, double bulletSpeed, double[] aims) {
    }
}
