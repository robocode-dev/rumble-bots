package fnl.bots.framework.sense;

/**
 * Raw input for one turn, translated by the adapter into core units (radians, counter-clockwise from east). It is
 * preallocated once and refilled every turn, so the adapter never allocates per turn.
 * <p>
 * Only Sense-phase stages read it; they turn it into derived state on the blackboard.
 */
public final class TurnInput {

    public static final int MAX_BOTS = 32;
    public static final int MAX_BULLET_EVENTS = 64;
    public static final int MAX_TEAM_MESSAGES = 16;

    public int round;
    public int turn;

    // Own state at this turn.
    public double x;
    public double y;
    public double heading;
    public double gunHeading;
    public double radarHeading;
    public double speed;
    public double energy;
    public double gunHeat;
    public int enemyCount;

    /** Microseconds left of this turn's budget when the input was taken; negative if unknown. */
    public int timeLeftMicros = -1;

    public final ScanRecord[] scans = newScans();
    public int scanCount;

    public final int[] deadBotIds = new int[MAX_BOTS];
    public int deadCount;

    public final BulletRecord[] bulletsFired = newBullets();
    public int bulletsFiredCount;
    public final BulletRecord[] bulletHits = newBullets();
    public int bulletHitCount;
    public final BulletRecord[] hitsTaken = newBullets();
    public int hitsTakenCount;

    /** Bots this bot collided with this turn, and whether this bot rammed them (drove into them). */
    public final int[] collisionBotIds = new int[MAX_BOTS];
    public final boolean[] collisionRammed = new boolean[MAX_BOTS];
    public int collisionCount;

    public final String[] teamMessages = new String[MAX_TEAM_MESSAGES];
    public final int[] teamMessageSenders = new int[MAX_TEAM_MESSAGES];
    public int teamMessageCount;

    public int skippedTurns;
    public boolean died;

    /** Clears the per-turn events; the adapter calls it before collecting the next turn. */
    public void clearEvents() {
        scanCount = 0;
        deadCount = 0;
        bulletsFiredCount = 0;
        bulletHitCount = 0;
        hitsTakenCount = 0;
        collisionCount = 0;
        for (int i = 0; i < teamMessageCount; i++) {
            teamMessages[i] = null;
        }
        teamMessageCount = 0;
        skippedTurns = 0;
        died = false;
    }

    /** Returns the next free scan slot, or null if the turn already holds {@link #MAX_BOTS} scans. */
    public ScanRecord addScan() {
        return scanCount < scans.length ? scans[scanCount++] : null;
    }

    public BulletRecord addBulletFired() {
        return bulletsFiredCount < bulletsFired.length ? bulletsFired[bulletsFiredCount++] : null;
    }

    public BulletRecord addBulletHit() {
        return bulletHitCount < bulletHits.length ? bulletHits[bulletHitCount++] : null;
    }

    public BulletRecord addHitTaken() {
        return hitsTakenCount < hitsTaken.length ? hitsTaken[hitsTakenCount++] : null;
    }

    public void addCollision(int botId, boolean rammed) {
        if (collisionCount < collisionBotIds.length) {
            collisionBotIds[collisionCount] = botId;
            collisionRammed[collisionCount++] = rammed;
        }
    }

    public void addDeath(int botId) {
        if (deadCount < deadBotIds.length) {
            deadBotIds[deadCount++] = botId;
        }
    }

    public void addTeamMessage(int senderId, String message) {
        if (teamMessageCount < teamMessages.length) {
            teamMessageSenders[teamMessageCount] = senderId;
            teamMessages[teamMessageCount++] = message;
        }
    }

    private static ScanRecord[] newScans() {
        ScanRecord[] records = new ScanRecord[MAX_BOTS];
        for (int i = 0; i < records.length; i++) {
            records[i] = new ScanRecord();
        }
        return records;
    }

    private static BulletRecord[] newBullets() {
        BulletRecord[] records = new BulletRecord[MAX_BULLET_EVENTS];
        for (int i = 0; i < records.length; i++) {
            records[i] = new BulletRecord();
        }
        return records;
    }
}
