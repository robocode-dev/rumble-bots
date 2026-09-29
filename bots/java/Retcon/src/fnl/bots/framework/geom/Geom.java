package fnl.bots.framework.geom;

/**
 * Allocation-free geometry on primitive coordinates. Angles follow {@link Angles}: radians, counter-clockwise from
 * east.
 */
public final class Geom {

    private Geom() {
    }

    /** Absolute direction from (x1, y1) to (x2, y2), in [0, 2π). */
    public static double bearing(double x1, double y1, double x2, double y2) {
        return Angles.normalizeAbsolute(Math.atan2(y2 - y1, x2 - x1));
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    public static double distanceSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /** X coordinate of the point at {@code distance} from x along {@code heading}. */
    public static double projectX(double x, double heading, double distance) {
        return x + Math.cos(heading) * distance;
    }

    /** Y coordinate of the point at {@code distance} from y along {@code heading}. */
    public static double projectY(double y, double heading, double distance) {
        return y + Math.sin(heading) * distance;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
