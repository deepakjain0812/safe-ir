package org.safeir.lab;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lab/admin")
public class FaultAdminController {
    public record FaultRequest(FaultState.Mode mode, long delayMs) {}
    private final FaultState state;
    private final String token;

    public FaultAdminController(FaultState state, @Value("${lab.token}") String token) {
        this.state = state;
        this.token = token;
    }

    @PostMapping("/fault")
    public ResponseEntity<?> change(@RequestHeader(value = "X-Lab-Token", required = false) String supplied,
                                    @RequestBody FaultRequest request) {
        if (supplied == null || !java.security.MessageDigest.isEqual(
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "not authorized"));
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "fault required"));
        }
        try {
            return ResponseEntity.ok(state.set(request.mode(), request.delayMs()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
