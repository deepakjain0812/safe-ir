package org.safeir.lab;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FaultStateTest {
    @Test void defaultsToNoFault() {
        FaultState f = new FaultState();
        assertEquals(FaultState.Mode.NONE, f.snapshot().mode());
        assertEquals(0, f.snapshot().revision());
    }

    @Test void changingFaultIncrementsRevision() {
        FaultState f = new FaultState();
        assertEquals(1, f.set(FaultState.Mode.ERROR, 0).revision());
        assertEquals(2, f.set(FaultState.Mode.NONE, 0).revision());
    }

    @Test void validatesBoundedFaultDelay() {
        FaultState f = new FaultState();
        assertThrows(IllegalArgumentException.class, () -> f.set(FaultState.Mode.LATENCY, 100));
        assertThrows(IllegalArgumentException.class, () -> f.set(FaultState.Mode.LATENCY, 1501));
        assertThrows(IllegalArgumentException.class, () -> f.set(null, 0));
        assertEquals(900, f.set(FaultState.Mode.LATENCY, 900).delayMs());
    }

    @Test void tracksActualHttpOutcomes() {
        FaultState f = new FaultState();
        f.record(true);
        f.record(false);
        assertEquals(2, f.requestCount());
        assertEquals(1, f.failureCount());
    }
}
