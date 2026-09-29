package fnl.bots.framework.blackboard;

/** The enemy currently selected as target. */
public interface TargetView {

    /** Index into {@link EnemiesView}, or {@link EnemiesView#NONE}. */
    int enemyIndex();

    default boolean hasTarget() {
        return enemyIndex() != EnemiesView.NONE;
    }
}
