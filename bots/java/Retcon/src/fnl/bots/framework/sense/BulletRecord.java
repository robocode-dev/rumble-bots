package fnl.bots.framework.sense;

/** A bullet-related event of the current turn, in core units. Which fields are set depends on the event kind. */
public final class BulletRecord {

    public int bulletId;
    /** The turn number the event was reported in. */
    public int turn;
    public double x;
    public double y;
    public double heading;
    public double power;
    /** The bot that fired the bullet. */
    public int ownerId;
    /** For hits: the bot that was hit. */
    public int victimId;
    /** For hits: the victim's energy after the hit. */
    public double victimEnergy;
}
