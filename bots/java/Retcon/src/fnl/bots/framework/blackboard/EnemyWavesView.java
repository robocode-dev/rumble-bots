package fnl.bots.framework.blackboard;

/** Enemy shots in flight, by index. Indices are only stable within a turn. */
public interface EnemyWavesView {

    int count();

    EnemyWaveView get(int index);

    /** Energy drops read as shots this game. */
    long firesDetected();

    /** Shots whose origin was unknown because the shooter was not scanned the turn before; no wave was made. */
    long unanchoredShots();

    /** Bullets that hit this bot this game, and how many of them belonged to a tracked wave. */
    long hitsTaken();

    long hitsOnWave();

    /** Waves whose power was misjudged from the energy drop and corrected from the bullet that hit this bot. */
    long powerCorrected();
}
