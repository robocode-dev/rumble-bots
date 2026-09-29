package fnl.bots.framework.roles;

import fnl.bots.framework.arbitration.BodyCommand;
import fnl.bots.framework.blackboard.Blackboard;

/** Decides the body's speed and turn. Owns the BODY actuator through the movement stage. */
public interface Movement {

    void decide(Blackboard bb, BodyCommand out);
}
