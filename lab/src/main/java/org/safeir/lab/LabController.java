package org.safeir.lab;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
public class LabController {
    private final FaultState faults;
    private final String role;
    private final RestClient client;
    private final String downstreamUrl;

    public LabController(FaultState faults, @Value("${lab.role}") String role,
                         @Value("${lab.downstream-url:}") String downstreamUrl) {
        this.faults = faults;
        this.role = role;
        this.downstreamUrl = downstreamUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping("/api/work")
    public ResponseEntity<Map<String, Object>> work() {
        var fault = faults.snapshot();
        if (fault.mode() == FaultState.Mode.LATENCY) {
            try {
                Thread.sleep(fault.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                faults.record(true);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("service", role, "error", "interrupted"));
            }
        }
        if (fault.mode() == FaultState.Mode.ERROR) {
            faults.record(true);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("service", role, "error", "injected_failure"));
        }
        if (!downstreamUrl.isBlank()) {
            try {
                var downstream = client.get().uri(downstreamUrl + "/api/work")
                    .retrieve().toBodilessEntity();
                if (!downstream.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("Downstream unhealthy");
                }
            } catch (RestClientException | IllegalStateException e) {
                faults.record(true);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("service", role, "error", "dependency_failure"));
            }
        }
        faults.record(false);
        return ResponseEntity.ok(Map.of("service", role, "status", "ok"));
    }

    @GetMapping("/lab/metrics")
    public Map<String, Object> metrics() {
        var current = faults.snapshot();
        return Map.of("service", role, "requestCount", faults.requestCount(),
            "failureCount", faults.failureCount(), "faultMode", current.mode().name(),
            "faultRevision", current.revision());
    }
}
