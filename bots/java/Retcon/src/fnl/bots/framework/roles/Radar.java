package fnl.bots.framework.roles;

import fnl.bots.framework.arbitration.RadarCommand;
import fnl.bots.framework.blackboard.Blackboard;

/** Decides the radar rotation. Owns the RADAR actuator through the radar stage. */
public interface Radar {

    void decide(Blackboard bb, RadarCommand out);
}
