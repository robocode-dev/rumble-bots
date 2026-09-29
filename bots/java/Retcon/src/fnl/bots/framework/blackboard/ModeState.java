package fnl.bots.framework.blackboard;

/** Mutable {@link ModeView}, written only by its Model-phase owner. */
public final class ModeState implements ModeView {

    public ModeKind kind = ModeKind.MELEE;
    public int enemiesAlive;
    public int teammatesAlive;

    @Override
    public ModeKind kind() {
        return kind;
    }

    @Override
    public int enemiesAlive() {
        return enemiesAlive;
    }

    @Override
    public int teammatesAlive() {
        return teammatesAlive;
    }
}
