package org.safeir.live;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.safeir.agent.OllamaAgent;
import org.safeir.agent.RuleBasedAgent;
import org.safeir.model.Domain.*;
import org.safeir.policy.EcraPolicyEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

class LiveRecoveryControllerTest {
    LiveRecoveryController controller = new LiveRecoveryController("test-only",
        new ObjectMapper(), new RuleBasedAgent(), mock(OllamaAgent.class), new EcraPolicyEngine());

    @Test void blocksUnauthenticatedCapture() {
        var result = controller.capture(null, new LiveRecoveryController.CaptureRequest("orders", false));
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }
    @Test void rejectsNonAllowlistedTargetsBeforeAnyRequest() {
        var result = controller.capture("test-only",
            new LiveRecoveryController.CaptureRequest("http://production", false));
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }
    @Test void rejectsUnknownCaptureAndDoesNotExecute() {
        var result = controller.execute("test-only",
            new LiveRecoveryController.ExecutionRequest("invalid", false));
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }
    @Test void blocksUnauthenticatedExecution() {
        var result = controller.execute(null, new LiveRecoveryController.ExecutionRequest("invalid", true));
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }
}
