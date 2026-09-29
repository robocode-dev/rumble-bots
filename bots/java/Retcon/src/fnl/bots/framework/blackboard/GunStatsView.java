package fnl.bots.framework.blackboard;

/** Virtual-gun statistics: how often each of the gun's aim models would have hit, over the whole game. */
public interface GunStatsView {

    /** Number of aim models tracked; 0 until the gun has fired. */
    int models();

    /** Simple class name of model {@code model}, for reports. */
    String modelName(int model);

    long virtualShots(int model);

    long virtualHits(int model);

    /** Hits over shots, or 0 without shots. */
    default double hitRate(int model) {
        long shots = virtualShots(model);
        return shots == 0 ? 0 : (double) virtualHits(model) / shots;
    }
}
