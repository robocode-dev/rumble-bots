package fnl.bots.framework.blackboard;

import fnl.bots.framework.sense.GameInfo;

/**
 * Derived state shared by the pipeline stages. It holds no raw events: Sense-phase stages turn the adapter's
 * {@code TurnInput} into these fields. Every field is a {@link Slot} with exactly one writer, claimed when the
 * pipeline is wired; everyone else reads through the view types.
 */
public final class Blackboard {

    private final GameInfo game;

    public final Slot<TurnView, TurnState> turnSlot = new Slot<>("turn", new TurnState());
    public final Slot<SelfView, SelfState> selfSlot = new Slot<>("self", new SelfState());
    public final Slot<EnemiesView, EnemyTable> enemiesSlot = new Slot<>("enemies", new EnemyTable());
    public final Slot<ModeView, ModeState> modeSlot = new Slot<>("mode", new ModeState());
    public final Slot<TargetView, TargetState> targetSlot = new Slot<>("target", new TargetState());
    public final Slot<EnemyWavesView, EnemyWaves> enemyWavesSlot = new Slot<>("enemyWaves", new EnemyWaves());
    public final Slot<GunStatsView, GunStats> gunStatsSlot = new Slot<>("gunStats", new GunStats());

    public Blackboard(GameInfo game) {
        this.game = game;
    }

    /** Game setup; fixed for the whole game. */
    public GameInfo game() {
        return game;
    }

    public TurnView turn() {
        return turnSlot.get();
    }

    public SelfView self() {
        return selfSlot.get();
    }

    public EnemiesView enemies() {
        return enemiesSlot.get();
    }

    public ModeView mode() {
        return modeSlot.get();
    }

    public TargetView target() {
        return targetSlot.get();
    }

    public EnemyWavesView enemyWaves() {
        return enemyWavesSlot.get();
    }

    public GunStatsView gunStats() {
        return gunStatsSlot.get();
    }

    /** The current target, or null if there is none. */
    public EnemyView targetEnemy() {
        int index = target().enemyIndex();
        return index == EnemiesView.NONE ? null : enemies().get(index);
    }
}
