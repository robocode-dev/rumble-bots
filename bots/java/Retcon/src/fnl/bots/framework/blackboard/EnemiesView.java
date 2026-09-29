package fnl.bots.framework.blackboard;

/** Read-only access to all tracked enemies, by index. Indices are stable within a round. */
public interface EnemiesView {

    int NONE = -1;

    /** Number of enemies tracked this round (alive or dead). */
    int count();

    EnemyView get(int index);

    /** Index of the enemy with the given bot id, or {@link #NONE}. */
    int indexOf(int botId);

    /** Tracked enemies not known to be dead. */
    int aliveCount();

    /** Enemies alive according to the platform, including enemies never scanned. */
    int reportedAliveCount();
}
