package fnl.bots.framework.arbitration;

/** Gun rotation for one turn, in radians/turn (counter-clockwise positive), independent of the body turn. */
public final class GunCommand {

    public double turnRate;

    public void clear() {
        turnRate = 0;
    }
}
