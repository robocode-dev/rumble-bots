package fnl.bots.framework.roles;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;

/** Chooses the firepower for the next shot. */
public interface FirePower {

    /** Returns the firepower to use against {@code target}, or 0 to hold fire. */
    double power(Blackboard bb, EnemyView target);
}
