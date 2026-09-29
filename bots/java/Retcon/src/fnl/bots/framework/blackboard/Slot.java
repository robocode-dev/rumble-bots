package fnl.bots.framework.blackboard;

/**
 * One blackboard field with exactly one writer. Readers see the value through its read-only view type {@code V};
 * the single owner claims the mutable type {@code M} once, when the pipeline is wired, and mutates it in place every
 * turn. A second claim fails fast, so the one-writer rule is enforced at construction time and costs nothing per turn.
 *
 * @param <V> read-only view type
 * @param <M> mutable type, owned by one writer
 */
public final class Slot<V, M extends V> {

    private final String name;
    private final M value;
    private Object owner;

    public Slot(String name, M value) {
        this.name = name;
        this.value = value;
    }

    /** The read-only view of the value. */
    public V get() {
        return value;
    }

    /**
     * Claims write access for {@code owner}.
     *
     * @throws IllegalStateException if the slot already has an owner
     */
    public M claim(Object owner) {
        if (this.owner != null) {
            throw new IllegalStateException(
                    "Blackboard slot '" + name + "' is already written by " + this.owner + "; rejected " + owner);
        }
        this.owner = owner;
        return value;
    }

    public Object owner() {
        return owner;
    }

    public String name() {
        return name;
    }
}
