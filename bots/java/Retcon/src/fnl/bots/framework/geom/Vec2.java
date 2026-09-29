package fnl.bots.framework.geom;

/** A mutable 2D point, meant to be preallocated and reused so hot paths do not allocate. */
public final class Vec2 {

    public double x;
    public double y;

    public Vec2 set(double x, double y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vec2 set(Vec2 other) {
        return set(other.x, other.y);
    }

    public double distanceTo(double ox, double oy) {
        return Geom.distance(x, y, ox, oy);
    }

    public double bearingTo(double ox, double oy) {
        return Geom.bearing(x, y, ox, oy);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
