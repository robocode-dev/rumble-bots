package fnl.bots.framework.stats;

/**
 * Per-game turn statistics: compute time per turn (a fixed histogram, so recording never allocates), skipped turns
 * per round and the first {@value #MAX_SKIPS_LISTED} skipped turns by round and turn, and turns over the compute
 * budget. The list tells a machine-wide stall (teammates skip the same turns) from a slow bot.
 */
public final class TurnStats {

    private static final int BUCKET_MICROS = 10;
    private static final int BUCKETS = 10_000; // 0..100 ms
    static final int MAX_SKIPS_LISTED = 256;

    private final long[] histogram = new long[BUCKETS + 1];
    private final int[] skippedPerRound;
    private final int[] skippedRounds = new int[MAX_SKIPS_LISTED];
    private final int[] skippedTurns = new int[MAX_SKIPS_LISTED];
    private long turns;
    private long maxMicros;
    private long overBudget;
    private int skippedTotal;
    private int computeBudgetMicros;
    private int turnTimeoutMicros;

    public TurnStats(int numberOfRounds) {
        this.skippedPerRound = new int[numberOfRounds + 1];
    }

    public void setBudget(int turnTimeoutMicros, int computeBudgetMicros) {
        this.turnTimeoutMicros = turnTimeoutMicros;
        this.computeBudgetMicros = computeBudgetMicros;
    }

    public void recordCompute(long micros) {
        turns++;
        if (micros > maxMicros) {
            maxMicros = micros;
        }
        if (computeBudgetMicros > 0 && micros > computeBudgetMicros) {
            overBudget++;
        }
        int bucket = (int) Math.min(BUCKETS, micros / BUCKET_MICROS);
        histogram[bucket]++;
    }

    /** Records one skipped turn: the round it was in and the turn number the server reported as skipped. */
    public void recordSkipped(int round, int turn) {
        if (round >= 0 && round < skippedPerRound.length) {
            skippedPerRound[round]++;
        }
        if (skippedTotal < MAX_SKIPS_LISTED) {
            skippedRounds[skippedTotal] = round;
            skippedTurns[skippedTotal] = turn;
        }
        skippedTotal++;
    }

    /** Compute time at the given percentile (0..100), in microseconds, rounded up to the histogram resolution. */
    public long percentileMicros(double percentile) {
        if (turns == 0) {
            return 0;
        }
        long rank = (long) Math.ceil(percentile / 100.0 * turns);
        long seen = 0;
        for (int i = 0; i < histogram.length; i++) {
            seen += histogram[i];
            if (seen >= rank) {
                return (long) (i + 1) * BUCKET_MICROS;
            }
        }
        return maxMicros;
    }

    /** Non-empty histogram buckets as {@code "bucketStartMicros:count,..."}, so readers can apply any budget. */
    public String histogram() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > 0) {
                if (!sb.isEmpty()) {
                    sb.append(',');
                }
                sb.append((long) i * BUCKET_MICROS).append(':').append(histogram[i]);
            }
        }
        return sb.toString();
    }

    public long turns() {
        return turns;
    }

    public long maxMicros() {
        return maxMicros;
    }

    public long overBudget() {
        return overBudget;
    }

    public int skippedTotal() {
        return skippedTotal;
    }

    public int[] skippedPerRound() {
        return skippedPerRound.clone();
    }

    /**
     * The listed skipped turns as {@code "round:turn ..."} in the order they happened, with {@code "+N"} appended
     * when more than {@value #MAX_SKIPS_LISTED} were skipped.
     */
    public String skippedTurnList() {
        StringBuilder sb = new StringBuilder();
        int listed = Math.min(skippedTotal, MAX_SKIPS_LISTED);
        for (int i = 0; i < listed; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(skippedRounds[i]).append(':').append(skippedTurns[i]);
        }
        if (skippedTotal > listed) {
            sb.append(" +").append(skippedTotal - listed);
        }
        return sb.toString();
    }

    public int computeBudgetMicros() {
        return computeBudgetMicros;
    }

    public int turnTimeoutMicros() {
        return turnTimeoutMicros;
    }
}
