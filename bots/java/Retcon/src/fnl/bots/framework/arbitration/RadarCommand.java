package fnl.bots.framework.arbitration;

/** Radar rotation for one turn, in radians/turn (counter-clockwise positive), independent of body and gun. */
public final class RadarCommand {

    public double turnRate;

    public void clear() {
        turnRate = 0;
    }
}
