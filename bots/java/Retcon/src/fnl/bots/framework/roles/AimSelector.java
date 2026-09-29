package fnl.bots.framework.roles;

import fnl.bots.framework.blackboard.Blackboard;

/** Chooses which of a gun's aim models to use this turn (a virtual-gun selector). */
public interface AimSelector {

    /** Returns an index into {@code models}. */
    int choose(Blackboard bb, AimModel[] models);
}
