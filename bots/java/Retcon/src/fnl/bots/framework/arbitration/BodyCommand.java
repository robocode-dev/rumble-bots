package fnl.bots.framework.arbitration;

/** Body output for one turn: target speed (units/turn) and turn rate (radians/turn, counter-clockwise positive). */
public final class BodyCommand {

    public double targetSpeed;
    public double turnRate;

    public void clear() {
        targetSpeed = 0;
        turnRate = 0;
    }
}
