package fnl.bots.framework.rules;

import fnl.bots.framework.geom.Angles;

/**
 * All game physics in one place. Strategies call these constants and functions; they never contain physics
 * literals such as 18, 8 or 20 − 3p.
 * <p>
 * Values and turn-order facts are verified against the Tank Royale server v1.3.1 source; every entry cites its
 * source in {@code docs/physics-facts.md}, and {@code RulesConformanceTest} pins each one. Angles and turn rates are
 * in radians (see {@link Angles}); distances in units; time in turns.
 * <p>
 * Book: <a href="https://book.robocode.dev/tank-royale/physics-differences.html">Physics Differences</a>.
 */
public final class Rules {

    private Rules() {
    }

    // ---- Bot body --------------------------------------------------------------------------------------------

    /** Radius of the circular hitbox used for bullet hits and scans ({@code BOT_BOUNDING_CIRCLE_RADIUS}). */
    public static final double BOT_HIT_RADIUS = 18.0;
    public static final double BOT_DIAMETER = 2 * BOT_HIT_RADIUS;
    public static final double MAX_SPEED = 8.0;
    public static final double ACCELERATION = 1.0;
    /** Deceleration magnitude when slowing down in the same direction. */
    public static final double DECELERATION = 2.0;
    public static final double INITIAL_ENERGY = 100.0;
    public static final double RAM_DAMAGE = 0.6;
    private static final double DISABLED_ENERGY_EPSILON = 1e-6;

    private static final double MAX_BODY_TURN_RATE = Math.toRadians(10.0);
    private static final double BODY_TURN_RATE_PER_SPEED = Math.toRadians(0.75);
    public static final double MAX_GUN_TURN_RATE = Math.toRadians(20.0);
    public static final double MAX_RADAR_TURN_RATE = Math.toRadians(45.0);
    public static final double RADAR_RANGE = 1200.0;

    // ---- Gun and bullets -------------------------------------------------------------------------------------

    public static final double MIN_FIREPOWER = 0.1;
    public static final double MAX_FIREPOWER = 3.0;
    public static final double INITIAL_GUN_HEAT = 3.0;
    private static final double BULLET_SPEED_BASE = 20.0;
    private static final double BULLET_SPEED_PER_POWER = 3.0;
    private static final double BULLET_HIT_ENERGY_GAIN_FACTOR = 3.0;
    public static final double MIN_BULLET_SPEED = bulletSpeed(MAX_FIREPOWER);
    public static final double MAX_BULLET_SPEED = bulletSpeed(MIN_FIREPOWER);

    public static double clampFirepower(double firepower) {
        return Math.max(MIN_FIREPOWER, Math.min(MAX_FIREPOWER, firepower));
    }

    /** Bullet speed in units per turn: 20 − 3·p, with p clamped to [0.1, 3]. */
    public static double bulletSpeed(double firepower) {
        return BULLET_SPEED_BASE - BULLET_SPEED_PER_POWER * clampFirepower(firepower);
    }

    /** Damage dealt by a bullet: 4·p, plus 2·(p − 1) when p &gt; 1. */
    public static double bulletDamage(double firepower) {
        double p = clampFirepower(firepower);
        double damage = 4 * p;
        if (firepower > 1) {
            damage += 2 * (firepower - 1);
        }
        return damage;
    }

    /** The smallest firepower whose {@link #bulletDamage} is at least {@code damage}, clamped to the legal range. */
    public static double firepowerForDamage(double damage) {
        double p = damage <= 4 ? damage / 4 : (damage + 2) / 6;
        return clampFirepower(p);
    }

    /** Gun heat added by firing: 1 + p/5. */
    public static double gunHeat(double firepower) {
        return 1 + clampFirepower(firepower) / 5;
    }

    /** Energy returned to the shooter when its bullet hits: 3·p (the bullet's power is already clamped). */
    public static double energyGainOnHit(double firepower) {
        return BULLET_HIT_ENERGY_GAIN_FACTOR * clampFirepower(firepower);
    }

    /**
     * Energy the shooter pays for firing. The server charges the <em>requested</em> firepower, not the clamped one,
     * so requesting more than {@link #MAX_FIREPOWER} wastes energy. Always clamp before firing.
     */
    public static double fireEnergyCost(double requestedFirepower) {
        return requestedFirepower;
    }

    /**
     * Whether the server will fire this request: the gun must be cold at the start of the turn, the power at least
     * {@link #MIN_FIREPOWER}, and the energy strictly greater than the requested power.
     */
    public static boolean canFire(double gunHeat, double energy, double requestedFirepower) {
        return gunHeat == 0.0 && requestedFirepower >= MIN_FIREPOWER && energy > requestedFirepower;
    }

    // ---- Turn rates ------------------------------------------------------------------------------------------

    /** Maximum body turn rate at the given speed: 10° − 0.75°·|v|, in radians per turn. */
    public static double maxBodyTurnRate(double speed) {
        return MAX_BODY_TURN_RATE - BODY_TURN_RATE_PER_SPEED * Math.abs(clampSpeed(speed));
    }

    /** The body turn rate the server applies for a requested {@code turnRate} at {@code speed}. */
    public static double limitBodyTurnRate(double turnRate, double speed) {
        double max = maxBodyTurnRate(speed);
        return Math.max(-max, Math.min(max, turnRate));
    }

    /**
     * A body intent updates, in this order: the speed ({@link #nextSpeed}), the position, moved by the new speed
     * along the <em>old</em> heading, and then the heading, turned by at most {@link #maxBodyTurnRate} of the
     * <em>new</em> speed. Classic Robocode turns before it moves.
     */
    public static final boolean BODY_MOVES_BEFORE_TURNING = true;

    /**
     * Where the server puts a bot that moved from ({@code oldX}, {@code oldY}) to ({@code x}, {@code y}) and crossed
     * a wall: back along the move, to {@link #BOT_HIT_RADIUS} from the wall. Written to {@code out} as {x, y}; the
     * bot hit a wall if either differs from the unconstrained position, and its speed then becomes 0. Mirrors the
     * server exactly, including that a y-wall recomputes x from the unconstrained move.
     */
    public static void constrainBotPosition(double oldX, double oldY, double x, double y, double arenaWidth,
                                            double arenaHeight, double[] out) {
        double newX = x;
        double newY = y;
        if (x - BOT_HIT_RADIUS < 0) {
            newX = BOT_HIT_RADIUS;
            newY = alongMove(oldY, y - oldY, newX - oldX, x - oldX, y);
        } else if (x + BOT_HIT_RADIUS > arenaWidth) {
            newX = arenaWidth - BOT_HIT_RADIUS;
            newY = alongMove(oldY, y - oldY, newX - oldX, x - oldX, y);
        }
        if (y - BOT_HIT_RADIUS < 0) {
            newY = BOT_HIT_RADIUS;
            newX = alongMove(oldX, x - oldX, newY - oldY, y - oldY, newX);
        } else if (y + BOT_HIT_RADIUS > arenaHeight) {
            newY = arenaHeight - BOT_HIT_RADIUS;
            newX = alongMove(oldX, x - oldX, newY - oldY, y - oldY, newX);
        }
        out[0] = newX;
        out[1] = newY;
    }

    /** old + delta · part / whole, or {@code fallback} when the move has no extent along the clamped axis. */
    private static double alongMove(double old, double delta, double part, double whole, double fallback) {
        return whole != 0 ? old + delta * part / whole : fallback;
    }

    /**
     * A living bot whose energy is within 1e-6 of 0 is disabled: the server drops its movement, so it stands still
     * while it may still report its old speed.
     */
    public static boolean isDisabled(double energy) {
        return Math.abs(energy) < DISABLED_ENERGY_EPSILON;
    }

    public static double clampSpeed(double speed) {
        return Math.max(-MAX_SPEED, Math.min(MAX_SPEED, speed));
    }

    /** Wall damage for hitting a wall at the given speed: |v|/2 − 1, never below 0. */
    public static double wallDamage(double speed) {
        return Math.max(0, Math.abs(clampSpeed(speed)) / 2 - 1);
    }

    /**
     * The speed after one turn, exactly as the server computes it ({@code calcNewBotSpeed}).
     * <p>
     * <b>Conformance note:</b> when the target speed has the other sign than the current speed, or is 0, the server
     * does not decelerate by {@link #DECELERATION}. A target of 0 stops the bot at once, and reversing gives
     * −(1 − |v|/2) at any speed (8 → 3). This contradicts the documented rules and is reported as a probable server
     * bug in {@code docs/physics-facts.md}; this method follows the server because the server is the truth.
     */
    public static double nextSpeed(double currentSpeed, double targetSpeed) {
        if (currentSpeed < 0.0) {
            return -nextSpeed(-currentSpeed, -targetSpeed);
        }
        if (currentSpeed > 0) {
            if (Math.signum(currentSpeed) == Math.signum(targetSpeed)) {
                double diff = targetSpeed - currentSpeed;
                if (diff >= 0) {
                    return Math.min(currentSpeed + Math.min(diff, ACCELERATION), MAX_SPEED);
                }
                return Math.max(currentSpeed + Math.max(diff, -DECELERATION), -MAX_SPEED);
            }
            if (targetSpeed == 0.0) {
                return 0.0;
            }
            double decelerationTime = currentSpeed / DECELERATION;
            return (1 - decelerationTime) * -ACCELERATION;
        }
        double diff = targetSpeed;
        double acceleration = Math.min(Math.abs(diff), ACCELERATION);
        return diff >= 0 ? Math.min(acceleration, MAX_SPEED) : Math.max(-acceleration, -MAX_SPEED);
    }

    // ---- Turn order and bullet timing ------------------------------------------------------------------------

    /**
     * With all three "adjust" flags set, the body, gun and radar headings each change by exactly their own turn
     * rate, so the actuators are independent. The Tank Royale adapter always sets them.
     */
    public static final boolean ACTUATORS_DECOUPLED = true;

    /**
     * A bot that decides to fire after seeing turn {@code t} gets its bullet in turn t + 1, but the bullet starts at
     * the bot's position observed at turn {@code t} and flies along the gun heading observed at turn {@code t}:
     * firing happens before the same intent's movement and gun rotation. So aim with the <em>current</em> gun
     * heading, not the heading after this turn's gun turn.
     */
    public static final boolean FIRE_USES_PRE_TURN_GUN_HEADING = true;

    /** Turn whose observed bot position is the origin of a bullet fired in response to turn {@code decidedAt}. */
    public static int firingPositionTurn(int decidedAt) {
        return decidedAt;
    }

    /** Turn in which the server creates a bullet fired in response to turn {@code decidedAt}. */
    public static int bulletCreatedTurn(int decidedAt) {
        return decidedAt + 1;
    }

    /**
     * Distance from its origin of a bullet fired in response to turn {@code decidedAt}, as reported at turn
     * {@code now}: v·(now − decidedAt). The bullet already moves v within the turn it is created.
     */
    public static double bulletDistance(int decidedAt, int now, double bulletSpeed) {
        return bulletSpeed * (now - decidedAt);
    }

    /**
     * Far end of the segment the server sweeps for hits in turn {@code now}: the hit test runs from
     * {@link #bulletDistance} to v·(now − decidedAt + 1) against each bot's post-move position.
     */
    public static double hitSweepEnd(int decidedAt, int now, double bulletSpeed) {
        return bulletSpeed * (now - decidedAt + 1);
    }

    /**
     * First turn in which a bullet fired in response to turn {@code decidedAt}, from ({@code originX},
     * {@code originY}) along {@code heading}, hits a bot standing still at ({@code targetX}, {@code targetY}).
     * It follows the server's hit test: in turn {@code now} the segment from {@link #bulletDistance} to
     * {@link #hitSweepEnd} is tested against the {@link #BOT_HIT_RADIUS} circle. The segment of the creation turn
     * starts at v, not 0, so a target closer than that is passed over.
     *
     * @return the turn of the hit, or −1 if the bullet misses
     */
    public static int firstHitTurnOnStillTarget(int decidedAt, double originX, double originY, double heading,
                                                double targetX, double targetY, double bulletSpeed) {
        double dx = targetX - originX;
        double dy = targetY - originY;
        double cos = Math.cos(heading);
        double sin = Math.sin(heading);
        double along = dx * cos + dy * sin;
        double lateral = Math.abs(-dx * sin + dy * cos);
        if (lateral > BOT_HIT_RADIUS) {
            return -1;
        }
        double halfChord = Math.sqrt(BOT_HIT_RADIUS * BOT_HIT_RADIUS - lateral * lateral);
        double entry = along - halfChord;
        double exit = along + halfChord;
        int turn = Math.max(bulletCreatedTurn(decidedAt), decidedAt - 1 + (int) Math.ceil(entry / bulletSpeed));
        return bulletDistance(decidedAt, turn, bulletSpeed) <= exit ? turn : -1;
    }

    // ---- Enemy fire seen through scans ------------------------------------------------------------------------

    /**
     * A scan in turn {@code k} reports the scanned bot's energy after that turn's fire cost, wall damage and ram
     * damage, but before that turn's bullet hits: the victim's damage and the shooter's {@link #energyGainOnHit}
     * bonus first show in the scan of turn k + 1 ({@link #scanTurnShowingHit}).
     */
    public static final boolean SCAN_ENERGY_BEFORE_BULLET_HITS = true;

    /** Wall damage applies only on the first turn of wall contact, not while the bot stays against the wall. */
    public static final boolean WALL_DAMAGE_FIRST_CONTACT_ONLY = true;

    /**
     * Decision turn of an enemy shot first seen as an energy drop in the scan of turn {@code detectedAt}. The fire
     * cost shows in the scan of the turn the bullet is created ({@link #bulletCreatedTurn}), so the shot was decided
     * one turn earlier, and its origin is the enemy's position scanned then ({@link #firingPositionTurn}).
     */
    public static int enemyFireDecisionTurn(int detectedAt) {
        return detectedAt - 1;
    }

    /** First turn whose scan shows the energy effect of a bullet hit reported in turn {@code hitTurn}. */
    public static int scanTurnShowingHit(int hitTurn) {
        return hitTurn + 1;
    }

    /** Half the angle a bot's hit circle covers at {@code distance}: atan(18 / d). */
    public static double hitHalfAngle(double distance) {
        return Math.atan(BOT_HIT_RADIUS / Math.max(distance, BOT_HIT_RADIUS));
    }
}
