package fnl.bots.framework.blackboard;

/** Mutable {@link EnemyView}, owned by the {@link EnemyTable} writer. */
public final class EnemyState implements EnemyView {

    public int id = -1;
    public boolean alive;
    public int lastSeenTurn = -1;
    public double x;
    public double y;
    public double heading;
    public double speed;
    public double energy;
    public double energyDrop;
    public double distance;
    public double bearing;

    void reset() {
        id = -1;
        alive = false;
        lastSeenTurn = -1;
        x = y = heading = speed = energy = energyDrop = distance = bearing = 0;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public boolean alive() {
        return alive;
    }

    @Override
    public int lastSeenTurn() {
        return lastSeenTurn;
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double heading() {
        return heading;
    }

    @Override
    public double speed() {
        return speed;
    }

    @Override
    public double energy() {
        return energy;
    }

    @Override
    public double energyDrop() {
        return energyDrop;
    }

    @Override
    public double distance() {
        return distance;
    }

    @Override
    public double bearing() {
        return bearing;
    }
}
