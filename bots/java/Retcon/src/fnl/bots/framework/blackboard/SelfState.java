package fnl.bots.framework.blackboard;

/** Mutable {@link SelfView}, written only by its Sense-phase owner. */
public final class SelfState implements SelfView {

    public double x;
    public double y;
    public double heading;
    public double gunHeading;
    public double radarHeading;
    public double speed;
    public double energy;
    public double gunHeat;
    public int teammatesAlive;

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
    public double gunHeading() {
        return gunHeading;
    }

    @Override
    public double radarHeading() {
        return radarHeading;
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
    public double gunHeat() {
        return gunHeat;
    }

    @Override
    public int teammatesAlive() {
        return teammatesAlive;
    }
}
