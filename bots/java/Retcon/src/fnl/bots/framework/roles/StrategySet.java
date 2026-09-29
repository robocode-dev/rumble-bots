package fnl.bots.framework.roles;

import java.util.Objects;

/** One complete set of role implementations, used while its mode is active. */
public record StrategySet(Radar radar, Movement movement, TargetSelector target, Gun gun, FirePower power,
                          TeamComms comms) {

    public StrategySet {
        Objects.requireNonNull(radar, "radar");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(gun, "gun");
        Objects.requireNonNull(power, "power");
        Objects.requireNonNull(comms, "comms");
    }
}
