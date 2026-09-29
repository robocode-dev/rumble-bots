package fnl.bots.framework.blackboard;

/** Fixed-capacity table of enemy states; preallocated, so tracking never allocates. */
public final class EnemyTable implements EnemiesView {

    public static final int CAPACITY = 32;

    private final EnemyState[] enemies = new EnemyState[CAPACITY];
    private int count;
    /** Enemies alive according to the platform, including enemies never scanned. */
    public int reportedAlive;

    public EnemyTable() {
        for (int i = 0; i < CAPACITY; i++) {
            enemies[i] = new EnemyState();
        }
    }

    public void clear() {
        for (int i = 0; i < count; i++) {
            enemies[i].reset();
        }
        count = 0;
    }

    /** Returns the state for {@code botId}, adding it if new; null if the table is full. */
    public EnemyState getOrAdd(int botId) {
        int index = indexOf(botId);
        if (index != NONE) {
            return enemies[index];
        }
        if (count == CAPACITY) {
            return null;
        }
        EnemyState enemy = enemies[count++];
        enemy.id = botId;
        enemy.alive = true;
        return enemy;
    }

    public EnemyState mutable(int index) {
        return enemies[index];
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public EnemyView get(int index) {
        return enemies[index];
    }

    @Override
    public int indexOf(int botId) {
        for (int i = 0; i < count; i++) {
            if (enemies[i].id == botId) {
                return i;
            }
        }
        return NONE;
    }

    @Override
    public int reportedAliveCount() {
        return reportedAlive;
    }

    @Override
    public int aliveCount() {
        int alive = 0;
        for (int i = 0; i < count; i++) {
            if (enemies[i].alive) {
                alive++;
            }
        }
        return alive;
    }
}
