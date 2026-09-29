package fnl.bots.retcon.mode;

import fnl.bots.framework.blackboard.ModeKind;
import fnl.bots.framework.roles.Mode;

/**
 * One enemy and no teammates is a duel; with a teammate and at most two enemies it is 2v2; anything else is melee.
 * <p>
 * Book: <a href="https://book.robocode.dev/melee-combat/melee-strategy.html">Melee Strategy</a> and
 * <a href="https://book.robocode.dev/team-strategies/team-basics.html">Team Basics</a>.
 */
public final class AliveCountMode implements Mode {

    @Override
    public ModeKind select(int enemiesAlive, int teammatesAlive) {
        if (teammatesAlive == 0 && enemiesAlive <= 1) {
            return ModeKind.ONE_VS_ONE;
        }
        if (teammatesAlive >= 1 && enemiesAlive <= 2) {
            return ModeKind.TWO_VS_TWO;
        }
        return ModeKind.MELEE;
    }
}
