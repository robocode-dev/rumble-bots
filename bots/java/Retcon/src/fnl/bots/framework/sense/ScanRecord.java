package fnl.bots.framework.sense;

/** One scanned bot in the current turn, in core units (radians, counter-clockwise from east). */
public final class ScanRecord {

    public int botId;
    public double x;
    public double y;
    public double heading;
    public double speed;
    public double energy;
}
