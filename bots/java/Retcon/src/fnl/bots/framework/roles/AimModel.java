package fnl.bots.framework.roles;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.EnemyView;

/** Turns collected data into a firing angle. */
public interface AimModel {

    /**
     * Returns the absolute angle (radians, counter-clockwise from east) to fire at {@code target} with a bullet of
     * {@code bulletSpeed}, from this bot's current position; {@link Double#NaN} when the model has no opinion.
     */
    double aim(Blackboard bb, EnemyView target, double bulletSpeed);
}
