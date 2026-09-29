package fnl.bots.framework.roles;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemiesView;

/** Picks the enemy to shoot at (and to lock the radar on). */
public interface TargetSelector {

    /** Returns an index into {@link Blackboard#enemies()}, or {@link EnemiesView#NONE}. */
    int select(Blackboard bb);
}
