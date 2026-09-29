package fnl.bots.framework.arbitration;

/** Fire output for one turn: the firepower to fire with, or 0 to hold fire. */
public final class FireCommand {

    public double firepower;

    public void clear() {
        firepower = 0;
    }
}
