package fnl.bots.framework.blackboard;

/** The active mode and the alive counts it was chosen from. */
public interface ModeView {

    ModeKind kind();

    int enemiesAlive();

    int teammatesAlive();
}
