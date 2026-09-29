package fnl.bots.framework.blackboard;

/** Fixed-capacity table of enemy waves; preallocated, so tracking never allocates. */
public final class EnemyWaves implements EnemyWavesView {

    public static final int CAPACITY = 64;

    private final EnemyWave[] waves = new EnemyWave[CAPACITY];
    private int count;
    public long firesDetected;
    public long unanchoredShots;
    public long hitsTaken;
    public long hitsOnWave;
    public long powerCorrected;
    /** Waves dropped because the table was full. */
    public long overflow;

    public EnemyWaves() {
        for (int i = 0; i < CAPACITY; i++) {
            waves[i] = new EnemyWave();
            waves[i].reset();
        }
    }

    /** Removes all waves; the game counters are kept. */
    public void clear() {
        for (int i = 0; i < count; i++) {
            waves[i].reset();
        }
        count = 0;
    }

    /** Returns a fresh wave slot, or null (counted as overflow) if the table is full. */
    public EnemyWave add() {
        if (count == CAPACITY) {
            overflow++;
            return null;
        }
        EnemyWave wave = waves[count++];
        wave.reset();
        return wave;
    }

    /** Removes the wave at {@code index} by moving the last wave into its place. */
    public void remove(int index) {
        EnemyWave removed = waves[index];
        waves[index] = waves[count - 1];
        waves[count - 1] = removed;
        removed.reset();
        count--;
    }

    public EnemyWave mutable(int index) {
        return waves[index];
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public EnemyWaveView get(int index) {
        return waves[index];
    }

    @Override
    public long firesDetected() {
        return firesDetected;
    }

    @Override
    public long unanchoredShots() {
        return unanchoredShots;
    }

    @Override
    public long hitsTaken() {
        return hitsTaken;
    }

    @Override
    public long hitsOnWave() {
        return hitsOnWave;
    }

    @Override
    public long powerCorrected() {
        return powerCorrected;
    }
}
