package fnl.bots.framework.arbitration;

/**
 * The resolved, platform-free output of one turn. Turn rates are in radians/turn, counter-clockwise positive, and
 * each actuator's rate is independent of the others; the adapter translates this to the platform.
 */
public final class Intent {

    public double targetSpeed;
    public double bodyTurnRate;
    public double gunTurnRate;
    public double radarTurnRate;
    /** Firepower to fire with this turn; 0 holds fire. */
    public double firepower;

    public void clear() {
        targetSpeed = 0;
        bodyTurnRate = 0;
        gunTurnRate = 0;
        radarTurnRate = 0;
        firepower = 0;
    }
}
