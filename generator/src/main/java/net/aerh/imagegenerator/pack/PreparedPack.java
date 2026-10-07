package net.aerh.imagegenerator.pack;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A fully loaded pack that is not live yet: built by {@link PackRepository#prepare}, inspected and
 * test-rendered through {@link #previewRepository()}, then published exactly once with
 * {@link PackRepository#register(PreparedPack)} or {@link PackRepository#replace(PreparedPack)}.
 *
 * <p>Lifecycle: OPEN, then either CLAIMED (published; the repository now owns the pack and
 * {@link #close()} does nothing) or CLOSED (released). Publishing a pack that is not OPEN throws
 * {@link IllegalStateException}. A failed publish returns the pack to OPEN so the caller can still
 * close it. Callers should close every prepared pack in a finally block; closing after a
 * successful publish is a safe no-op.
 */
public final class PreparedPack implements AutoCloseable {

    private enum State {OPEN, CLAIMED, CLOSED}

    private final LoadedPack pack;
    private final Optional<PackFormatRange> declaredFormat;
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);

    PreparedPack(LoadedPack pack, Optional<PackFormatRange> declaredFormat) {
        this.pack = pack;
        this.declaredFormat = declaredFormat;
    }

    public PackId id() {
        return pack.id();
    }

    /** The pack's own id plus the pack it is a variant of, if any. */
    public PackLineage lineage() {
        return pack.lineage();
    }

    public Set<String> assetNamespaces() {
        return pack.assetNamespaces();
    }

    /** Sorted item refs that indexed without errors, in the same form the generators accept. */
    public List<String> itemRefs() {
        return pack.itemRefs();
    }

    /** The format range from the pack's {@code pack.mcmeta}, empty when missing or unusable. */
    public Optional<PackFormatRange> declaredFormat() {
        return declaredFormat;
    }

    /**
     * A repository holding only this pack, for test renders before publishing. It does not own
     * the pack: unregistering from it never releases anything. The preview resolves against the
     * underlying pack for as long as that pack is alive: it stops working when this handle is
     * closed unpublished, or, after publishing, when the published pack is replaced (after the
     * grace period) or unregistered.
     *
     * @throws IllegalStateException when this pack is no longer OPEN
     */
    public PackRepository previewRepository() {
        if (state.get() != State.OPEN) {
            throw new IllegalStateException("Prepared pack " + pack.id() + " is " + state.get() + " and cannot be previewed");
        }
        return PackRepository.preview(pack);
    }

    LoadedPack claim() {
        if (!state.compareAndSet(State.OPEN, State.CLAIMED)) {
            throw new IllegalStateException("Prepared pack " + pack.id() + " is " + state.get() + " and cannot be published");
        }
        return pack;
    }

    void unclaim() {
        state.compareAndSet(State.CLAIMED, State.OPEN);
    }

    @Override
    public void close() {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) {
            pack.release();
        }
    }
}
