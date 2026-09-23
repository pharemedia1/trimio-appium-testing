package org.example.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set of response times, with the percentiles a performance assertion should actually be made on.
 *
 * <p>Deliberately not a mean. An average hides the tail, and the tail is the part users feel: a
 * route that answers in 40 ms nineteen times and 4 s once averages 240 ms and looks fine, while one
 * user in twenty waited four seconds. So the suites assert on <b>p95</b> and record the max.
 *
 * <p>Also tracks failures separately from timings, because a fast error is not a fast response —
 * a route that starts 500ing immediately would otherwise show the best latency in the run.
 */
public final class LatencySample {

    private final String label;
    private final List<Long> millis = new ArrayList<>();
    private int failures;
    private int lastStatus;

    public LatencySample(String label) {
        this.label = label;
    }

    /** Records one response: its duration, and whether it was an error. */
    public synchronized void record(long durationMillis, int status) {
        millis.add(durationMillis);
        lastStatus = status;
        if (status >= 400) {
            failures++;
        }
    }

    public String label() {
        return label;
    }

    public synchronized int count() {
        return millis.size();
    }

    public synchronized int failures() {
        return failures;
    }

    public synchronized int lastStatus() {
        return lastStatus;
    }

    /** The share of responses that were 4xx or 5xx, in the range 0..1. */
    public synchronized double errorRate() {
        return millis.isEmpty() ? 0 : (double) failures / millis.size();
    }

    public synchronized long p50() {
        return percentile(50);
    }

    public synchronized long p95() {
        return percentile(95);
    }

    public synchronized long max() {
        List<Long> sorted = sorted();
        return sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);
    }

    /**
     * The {@code n}th percentile, by nearest rank.
     *
     * <p>Nearest rank rather than interpolation: with the sample sizes a test suite can afford
     * (tens, not thousands) an interpolated percentile invents a number that was never measured,
     * and the assertion message is more useful when it quotes a real observation.
     */
    public synchronized long percentile(int n) {
        List<Long> sorted = sorted();
        if (sorted.isEmpty()) {
            return 0;
        }
        int rank = (int) Math.ceil(n / 100.0 * sorted.size());
        return sorted.get(Math.min(Math.max(rank, 1), sorted.size()) - 1);
    }

    private List<Long> sorted() {
        List<Long> copy = new ArrayList<>(millis);
        Collections.sort(copy);
        return copy;
    }

    @Override
    public synchronized String toString() {
        return String.format("%-46s n=%-4d p50=%-6d p95=%-6d max=%-6d errors=%d",
                label, count(), p50(), p95(), max(), failures);
    }
}
