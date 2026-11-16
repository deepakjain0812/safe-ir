package org.safeir.lab;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class FaultState {
    public enum Mode { NONE, ERROR, LATENCY }
    public record Snapshot(Mode mode, long delayMs, long revision) {}
    private final AtomicReference<Snapshot> current =
        new AtomicReference<>(new Snapshot(Mode.NONE, 0, 0));
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public Snapshot snapshot() { return current.get(); }

    public synchronized Snapshot set(Mode mode, long delayMs) {
        if (mode == null || delayMs < 0 || delayMs > 2500) {
            throw new IllegalArgumentException("Invalid fault mode or delay (0-2500 ms)");
        }
        if (mode == Mode.LATENCY && delayMs < 200) {
            throw new IllegalArgumentException("Latency fault must delay at least 200 ms");
        }
        Snapshot next = new Snapshot(mode, mode == Mode.LATENCY ? delayMs : 0,
            current.get().revision() + 1);
        current.set(next);
        return next;
    }

    public void record(boolean failed) {
        requests.incrementAndGet();
        if (failed) failures.incrementAndGet();
    }

    public long requestCount() { return requests.get(); }
    public long failureCount() { return failures.get(); }
}
