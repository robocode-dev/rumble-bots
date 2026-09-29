package fnl.bots.framework.stages;

import fnl.bots.framework.blackboard.Blackboard;
import fnl.bots.framework.blackboard.ModeKind;
import fnl.bots.framework.roles.Gun;
import fnl.bots.framework.roles.StrategySet;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The strategy set for every mode. Lookups are allocation-free. */
public final class Strategies {

    private final EnumMap<ModeKind, StrategySet> sets;
    private final Gun[] distinctGuns;

    /** @param sets a strategy set for every {@link ModeKind} */
    public Strategies(Map<ModeKind, StrategySet> sets) {
        this.sets = new EnumMap<>(sets);
        for (ModeKind kind : ModeKind.values()) {
            if (!this.sets.containsKey(kind)) {
                throw new IllegalArgumentException("No strategy set for mode " + kind);
            }
        }
        List<Gun> guns = new ArrayList<>();
        for (StrategySet set : this.sets.values()) {
            if (guns.stream().noneMatch(g -> g == set.gun())) {
                guns.add(set.gun());
            }
        }
        this.distinctGuns = guns.toArray(new Gun[0]);
    }

    /** The strategy set of the mode currently on the blackboard. */
    public StrategySet active(Blackboard bb) {
        return sets.get(bb.mode().kind());
    }

    /** Every distinct gun across all modes; their collectors run whatever the mode. */
    Gun[] distinctGuns() {
        return distinctGuns;
    }
}
