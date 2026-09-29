package fnl.bots.framework.adapter.tankroyale;

import fnl.bots.framework.geom.Angles;

/**
 * Tank Royale uses degrees, counter-clockwise from east, with positive turn rates turning left (counter-clockwise).
 * The core uses the same orientation in radians, so conversion is a unit change only.
 */
final class TankRoyaleAngles {

    private TankRoyaleAngles() {
    }

    static double headingToCore(double degrees) {
        return Angles.normalizeAbsolute(Math.toRadians(degrees));
    }

    static double turnRateToPlatform(double radiansPerTurn) {
        return Math.toDegrees(radiansPerTurn);
    }
}
