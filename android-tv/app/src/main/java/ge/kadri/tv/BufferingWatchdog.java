package ge.kadri.tv;

/** Offer recovery once per continuous buffering period; a new seek resets the deadline. */
final class BufferingWatchdog {
    static final long WAIT_MS = 20_000;
    private long since = -1;
    private boolean offered;

    boolean shouldOffer(long now, boolean buffering, boolean wantsPlayback) {
        if (!buffering || !wantsPlayback) { reset(); return false; }
        if (since < 0) since = now;
        if (!offered && now - since >= WAIT_MS) { offered = true; return true; }
        return false;
    }

    void reset() { since = -1; offered = false; }
}
