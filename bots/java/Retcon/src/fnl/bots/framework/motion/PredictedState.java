package fnl.bots.framework.motion;

/** A bot's body state for movement prediction. Mutable and reused, so predicting never allocates. */
public final class PredictedState {

    public double x;
    public double y;
    /** Radians, counter-clockwise from east. */
    public double heading;
    public double speed;
    /** Turns stepped since {@link #set}. */
    public int turns;
    /** Whether any step so far hit a wall. */
    public boolean hitWall;

    public PredictedState set(double x, double y, double heading, double speed) {
        this.x = x;
        this.y = y;
        this.heading = heading;
        this.speed = speed;
        this.turns = 0;
        this.hitWall = false;
        return this;
    }

    public PredictedState copyFrom(PredictedState other) {
        x = other.x;
        y = other.y;
        heading = other.heading;
        speed = other.speed;
        turns = other.turns;
        hitWall = other.hitWall;
        return this;
    }
}
