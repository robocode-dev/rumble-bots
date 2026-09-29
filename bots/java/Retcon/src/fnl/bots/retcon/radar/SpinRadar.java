package fnl.bots.retcon.radar;

import fnl.bots.framework.arbitration.RadarCommand;
import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.roles.Radar;
import fnl.bots.framework.rules.Rules;

/**
 * Spins the radar at full rate, sweeping the whole field every eight turns. The M1 melee radar.
 * <p>
 * Book: <a href="https://book.robocode.dev/radar/one-on-one-radar/spinning-radar.html">Spinning Radar</a>.
 */
public final class SpinRadar implements Radar {

    @Override
    public void decide(Blackboard bb, RadarCommand out) {
        out.turnRate = Rules.MAX_RADAR_TURN_RATE;
    }
}
