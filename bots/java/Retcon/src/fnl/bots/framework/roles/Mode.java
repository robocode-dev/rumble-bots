package fnl.bots.framework.roles;

import fnl.bots.framework.blackboard.ModeKind;

/** Chooses the battle mode, and with it the active {@link StrategySet}, from who is still alive. */
public interface Mode {

    ModeKind select(int enemiesAlive, int teammatesAlive);
}
