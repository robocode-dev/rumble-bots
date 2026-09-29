package fnl.bots.framework.arbitration;

import fnl.bots.framework.geom.Geom;
import fnl.bots.framework.rules.Rules;

import java.util.EnumMap;
import java.util.Map;

/**
 * Enforces exactly one owner per {@link Actuator}. Owners claim their command object once, while the pipeline is
 * wired; each turn the arbiter clears all commands before the Decide phase and resolves them into an {@link Intent}
 * afterwards, clamping everything to the {@link Rules} limits. An unclaimed actuator stays idle.
 */
public final class Arbiter {

    private final Map<Actuator, Object> owners = new EnumMap<>(Actuator.class);

    private final BodyCommand body = new BodyCommand();
    private final GunCommand gun = new GunCommand();
    private final RadarCommand radar = new RadarCommand();
    private final FireCommand fire = new FireCommand();

    public BodyCommand claimBody(Object owner) {
        claim(Actuator.BODY, owner);
        return body;
    }

    public GunCommand claimGun(Object owner) {
        claim(Actuator.GUN, owner);
        return gun;
    }

    public RadarCommand claimRadar(Object owner) {
        claim(Actuator.RADAR, owner);
        return radar;
    }

    public FireCommand claimFire(Object owner) {
        claim(Actuator.FIRE, owner);
        return fire;
    }

    public Object ownerOf(Actuator actuator) {
        return owners.get(actuator);
    }

    /** Clears all commands; called before the Decide phase. */
    public void beginTurn() {
        body.clear();
        gun.clear();
        radar.clear();
        fire.clear();
    }

    /**
     * Resolves this turn's commands into {@code out}, clamped to the physics limits.
     *
     * @param currentSpeed the bot's speed, which limits the body turn rate
     */
    public void resolve(double currentSpeed, Intent out) {
        double maxBodyTurn = Rules.maxBodyTurnRate(currentSpeed);
        out.targetSpeed = Rules.clampSpeed(body.targetSpeed);
        out.bodyTurnRate = Geom.clamp(body.turnRate, -maxBodyTurn, maxBodyTurn);
        out.gunTurnRate = Geom.clamp(gun.turnRate, -Rules.MAX_GUN_TURN_RATE, Rules.MAX_GUN_TURN_RATE);
        out.radarTurnRate = Geom.clamp(radar.turnRate, -Rules.MAX_RADAR_TURN_RATE, Rules.MAX_RADAR_TURN_RATE);
        out.firepower = fire.firepower >= Rules.MIN_FIREPOWER ? Rules.clampFirepower(fire.firepower) : 0;
    }

    private void claim(Actuator actuator, Object owner) {
        Object existing = owners.putIfAbsent(actuator, owner);
        if (existing != null) {
            throw new IllegalStateException(
                    "Actuator " + actuator + " is already owned by " + existing + "; rejected " + owner);
        }
    }
}
