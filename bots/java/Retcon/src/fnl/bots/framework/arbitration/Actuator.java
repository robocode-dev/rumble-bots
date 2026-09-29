package fnl.bots.framework.arbitration;

/** The bot's independent outputs. Each has exactly one owner, claimed from the {@link Arbiter} at wiring time. */
public enum Actuator {
    BODY,
    GUN,
    RADAR,
    FIRE
}
