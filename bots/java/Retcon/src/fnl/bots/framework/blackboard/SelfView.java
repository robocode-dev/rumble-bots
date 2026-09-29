package fnl.bots.framework.blackboard;

/** This bot's own state at the current turn. Angles in radians, counter-clockwise from east. */
public interface SelfView {

    double x();

    double y();

    double heading();

    double gunHeading();

    double radarHeading();

    double speed();

    double energy();

    double gunHeat();

    int teammatesAlive();
}
