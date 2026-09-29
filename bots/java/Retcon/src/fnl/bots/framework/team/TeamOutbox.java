package fnl.bots.framework.team;

/** Team messages to broadcast at the end of this turn; the adapter sends and clears them. */
public final class TeamOutbox {

    /** The server accepts at most 5 team messages per bot per turn. */
    public static final int CAPACITY = 5;

    private final String[] messages = new String[CAPACITY];
    private int count;

    /** Queues a broadcast; returns false when this turn's capacity is used up. */
    public boolean broadcast(String message) {
        if (count == CAPACITY) {
            return false;
        }
        messages[count++] = message;
        return true;
    }

    public int count() {
        return count;
    }

    public String message(int index) {
        return messages[index];
    }

    public void clear() {
        for (int i = 0; i < count; i++) {
            messages[i] = null;
        }
        count = 0;
    }
}
