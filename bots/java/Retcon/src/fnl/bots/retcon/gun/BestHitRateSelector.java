package fnl.bots.retcon.gun;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.GunStatsView;
import fnl.bots.framework.roles.AimModel;
import fnl.bots.framework.roles.AimSelector;

/**
 * Picks the aim model whose virtual bullets have hit most often this game. Ties, including the start of a game,
 * go to the first model.
 * <p>
 * Book: <a href="https://book.robocode.dev/targeting/simple-targeting/virtual-guns-mean-targeting.html">Virtual
 * Guns</a> (step 3, select the best gun).
 */
public final class BestHitRateSelector implements AimSelector {

    @Override
    public int choose(Blackboard bb, AimModel[] models) {
        GunStatsView stats = bb.gunStats();
        int best = 0;
        for (int m = 1; m < stats.models() && m < models.length; m++) {
            if (stats.hitRate(m) > stats.hitRate(best)) {
                best = m;
            }
        }
        return best;
    }
}
