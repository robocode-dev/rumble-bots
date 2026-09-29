package fnl.bots.framework.blackboard;

/** Last known state of one enemy. Angles in radians, counter-clockwise from east. */
public interface EnemyView {

    int id();

    boolean alive();

    /** Turn of the latest scan; −1 if never scanned this round. */
    int lastSeenTurn();

    double x();

    double y();

    double heading();

    double speed();

    double energy();

    /** Energy drop since the previous scan (positive when the enemy lost energy), 0 if unknown. */
    double energyDrop();

    /** Distance from this bot at the time of the latest scan. */
    double distance();

    /** Absolute bearing from this bot at the time of the latest scan. */
    double bearing();
}
