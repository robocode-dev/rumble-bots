package fnl.bots.framework.stats;

import fnl.bots.framework.blackboard.EnemyWaveView;
import fnl.bots.framework.blackboard.EnemyWavesView;
import fnl.bots.framework.rules.Rules;

/**
 * Checks physics fact 5 against the live server: a bullet that hits this bot lies exactly
 * {@link Rules#bulletDistance} from the origin of the wave made from the shooter's energy drop. A residual of
 * ±v instead of 0 would mean the wave is one turn off. Allocation-free.
 */
public final class WaveProbe {

    private static final double EPSILON = 1e-6;
    private static final int MAX_MISMATCHES = 5;

    public long hitsChecked;
    public long exact;
    /** Hits one bullet step off: the wave's decision turn would be one turn early or late. */
    public long offByOneStep;
    public long otherResidual;
    public final StringBuilder mismatches = new StringBuilder();
    private int mismatchCount;

    /** Call once per turn, after the pipeline step, with the turn's waves. */
    public void observe(EnemyWavesView waves, int now) {
        for (int i = 0; i < waves.count(); i++) {
            EnemyWaveView wave = waves.get(i);
            if (wave.hitTurn() != now) {
                continue;
            }
            hitsChecked++;
            double distance = Math.hypot(wave.hitX() - wave.originX(), wave.hitY() - wave.originY());
            double residual = distance - wave.radius(now);
            if (Math.abs(residual) <= EPSILON) {
                exact++;
            } else if (Math.abs(Math.abs(residual) - wave.bulletSpeed()) <= EPSILON) {
                offByOneStep++;
            } else {
                otherResidual++;
            }
            if (Math.abs(residual) > EPSILON && mismatchCount < MAX_MISMATCHES) {
                mismatchCount++;
                if (!mismatches.isEmpty()) {
                    mismatches.append("; ");
                }
                mismatches.append("turn ").append(now).append(" decided ").append(wave.decisionTurn())
                        .append(" v ").append(wave.bulletSpeed()).append(" residual ").append(residual);
            }
        }
    }
}
