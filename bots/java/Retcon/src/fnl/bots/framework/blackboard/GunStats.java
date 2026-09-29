package fnl.bots.framework.blackboard;

/** Mutable {@link GunStatsView}, owned by the one virtual-gun collector that claims it. */
public final class GunStats implements GunStatsView {

    public static final int MAX_MODELS = 8;

    public final String[] names = new String[MAX_MODELS];
    public final long[] shots = new long[MAX_MODELS];
    public final long[] hits = new long[MAX_MODELS];
    public int models;

    @Override
    public int models() {
        return models;
    }

    @Override
    public String modelName(int model) {
        return names[model];
    }

    @Override
    public long virtualShots(int model) {
        return shots[model];
    }

    @Override
    public long virtualHits(int model) {
        return hits[model];
    }
}
