package fnl.bots.framework.blackboard;

/** Mutable {@link TargetView}, written only by its Decide-phase owner. */
public final class TargetState implements TargetView {

    public int enemyIndex = EnemiesView.NONE;

    @Override
    public int enemyIndex() {
        return enemyIndex;
    }
}
