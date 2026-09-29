package fnl.bots.framework.blackboard;

/**
 * One enemy shot in flight, detected from the shooter's energy drop (physics facts 4 and 5). Angles in radians,
 * counter-clockwise from east.
 */
public interface EnemyWaveView {

    int shooterId();

    /** Turn the shooter decided the shot: the bullet starts at its position scanned then. */
    int decisionTurn();

    double originX();

    double originY();

    double power();

    double bulletSpeed();

    /** Absolute angle from the origin to this bot at {@link #decisionTurn}: GuessFactor 0. */
    double directAngle();

    /** +1 or −1: this bot's lateral direction around the origin at {@link #decisionTurn}. */
    int lateralDirection();

    /** Largest angle this bot could reach from {@link #directAngle} before the bullet arrives: asin(8 / v). */
    double maxEscapeAngle();

    /** Distance of the bullet from the origin at turn {@code now}. */
    double radius(int now);

    /** Turn this wave's bullet hit this bot, or −1. */
    int hitTurn();

    /** Where the bullet was when it hit this bot; valid when {@link #hitTurn} ≥ 0. */
    double hitX();

    double hitY();
}
