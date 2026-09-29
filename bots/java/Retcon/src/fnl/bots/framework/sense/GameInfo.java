package fnl.bots.framework.sense;

/**
 * Game setup facts, fixed for a whole game and filled by the adapter when the game starts. Platform-free.
 */
public final class GameInfo {

    public int myId;
    public int arenaWidth;
    public int arenaHeight;
    public int numberOfRounds;
    public double gunCoolingRate;
    /** Server turn timeout in microseconds, as reported by the platform at runtime. */
    public int turnTimeoutMicros;
    /** Ids of teammates; empty when not in a team. Never modified after the game starts. */
    public int[] teammateIds = new int[0];

    public boolean isTeammate(int botId) {
        for (int id : teammateIds) {
            if (id == botId) {
                return true;
            }
        }
        return false;
    }
}
