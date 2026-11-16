package org.safeir.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.safeir.agent.OllamaAgent;
import org.safeir.agent.RuleBasedAgent;
import org.safeir.model.Domain.*;
import org.safeir.policy.EcraPolicyEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Lab-only controller. No arbitrary URLs, commands, Docker socket or production credentials.
 * NEVER enable outside disposable local experiments.
 */
@RestController
@RequestMapping("/lab/recovery")
@ConditionalOnProperty(name = "safeir.lab.enabled", havingValue = "true")
public class LiveRecoveryController {
    public record CaptureRequest(String service, Boolean useOllama) {}
    public record ExecutionRequest(String captureId, boolean guarded) {}
    public record Evidence(String service, int samples, int failed, double errorRate,
                           int medianLatencyMs, boolean complete, boolean unhealthy) {}
    public record Captured(String id, String service, Proposal proposal, Instant created,
                           Set<String> arms) {}
    public record CaptureResponse(String captureId, String service, String action,
                                  String proposalSource, Evidence evidence) {}
    public record ExecutionResponse(String captureId, String arm, String action,
                                    String decision, String reason, boolean applied,
                                    boolean verified, Evidence before, Evidence after,
                                    long elapsedMillis) {}

    private static final int SAMPLES = 5;
    private static final Duration MAX_AGE = Duration.ofMinutes(10);
    private static final Map<String, String> HOSTS = Map.of(
        "orders", "http://orders:8080",
        "payments", "http://payments:8080",
        "inventory", "http://inventory:8080");
    private final ConcurrentHashMap<String, Captured> captures = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper;
    private final String token;
    private final RuleBasedAgent rules;
    private final OllamaAgent ollama;
    private final EcraPolicyEngine policy;

    public LiveRecoveryController(@Value("${safeir.lab.token}") String token,
                                  ObjectMapper mapper, RuleBasedAgent rules,
                                  OllamaAgent ollama, EcraPolicyEngine policy) {
        this.token = token;
        this.mapper = mapper;
        this.rules = rules;
        this.ollama = ollama;
        this.policy = policy;
    }

    private boolean authorized(String supplied) {
        return supplied != null && java.security.MessageDigest.isEqual(
            supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String host(String service) {
        if (service == null || !HOSTS.containsKey(service)) {
            throw new IllegalArgumentException("Unknown lab service");
        }
        return HOSTS.get(service);
    }

    private HttpResponse<String> request(String url, String body, boolean admin) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5));
        if (body == null) b.GET();
        else b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (admin) b.header("X-Lab-Token", token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Evidence sample(String service) {
        String url = host(service) + "/api/work";
        int failures = 0;
        int valid = 0;
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            try {
                var response = request(url, null, false);
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                durations.add(elapsed);
                valid++;
                if (response.statusCode() < 200 || response.statusCode() >= 300) failures++;
            } catch (Exception e) {
                durations.add((System.nanoTime() - start) / 1_000_000);
                failures++;
            }
        }
        durations.sort(Long::compareTo);
        double errorRate = (double) failures / SAMPLES;
        return new Evidence(service, SAMPLES, failures, errorRate,
            Math.toIntExact(Math.min(Integer.MAX_VALUE, durations.get(durations.size()/2))),
            valid == SAMPLES, errorRate > 0.2);
    }

    private Incident incident(Evidence e) {
        return new Incident(UUID.randomUUID().toString(), "live-http", e.service(),
            e.unhealthy(), e.medianLatencyMs(), 0, e.errorRate(), e.complete(),
            false, null, null);
    }

    @PostMapping("/capture")
    public ResponseEntity<?> capture(@RequestHeader(value = "X-Lab-Token", required = false) String credential,
                                     @RequestBody CaptureRequest request) {
        if (!authorized(credential)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.service() == null || !HOSTS.containsKey(request.service()))
            return ResponseEntity.badRequest().body(Map.of("error", "Known service required"));
        if (captures.size() > 100) captures.clear();
        var evidence = sample(request.service());
        var incident = incident(evidence);
        Proposal proposal;
        try {
            proposal = Boolean.TRUE.equals(request.useOllama()) ? ollama.propose(incident) : rules.propose(incident);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Proposal generation failed"));
        }
        String id = UUID.randomUUID().toString();
        captures.put(id, new Captured(id, request.service(), proposal, Instant.now(),
            ConcurrentHashMap.newKeySet()));
        return ResponseEntity.ok(new CaptureResponse(id, request.service(),
            proposal.action().name(), proposal.source(), evidence));
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestHeader(value = "X-Lab-Token", required = false) String credential,
                                     @RequestBody ExecutionRequest request) {
        if (!authorized(credential)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.captureId() == null) return ResponseEntity.badRequest().build();
        Captured capture = captures.get(request.captureId());
        if (capture == null || capture.created().isBefore(Instant.now().minus(MAX_AGE))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Expired or unknown proposal"));
        }
        String arm = request.guarded() ? "ECRA" : "BASELINE";
        if (!capture.arms().add(arm)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Arm already used"));
        }
        long start = System.nanoTime();
        Evidence before = sample(capture.service());
        PolicyResult decision = request.guarded()
            ? policy.evaluate(incident(before), capture.proposal())
            : new PolicyResult(Decision.ALLOW, "No ECRA (lab executor boundary still enforced)", 0, 0);
        boolean applied = false;
        String reason = decision.reason();
        // No free-form commands: only a restart-like reset of an observed failing service.
        // This is a trusted fault-flag reset, NOT an actual container restart.
        if (decision.decision() == Decision.ALLOW && capture.proposal().action() == Action.RESTART_SERVICE) {
            if (before.complete() && before.unhealthy()) {
                try {
                    String reset = mapper.writeValueAsString(Map.of("mode", "NONE", "delayMs", 0));
                    var result = request(host(capture.service()) + "/lab/admin/fault", reset, true);
                    applied = result.statusCode() == 200;
                    if (!applied) reason = "Lab reset rejected";
                } catch (Exception ex) { reason = "Lab reset failed"; }
            } else reason = "Executor requires complete evidence of active HTTP failure";
        } else if (capture.proposal().action() != Action.NO_OP && decision.decision() == Decision.ALLOW) {
            reason = "Action not supported by restricted lab executor";
        }
        Evidence after = sample(capture.service());
        boolean verified = applied && after.complete() && after.failed() == 0;
        return ResponseEntity.ok(new ExecutionResponse(capture.id(), arm,
            capture.proposal().action().name(), decision.decision().name(), reason,
            applied, verified, before, after, (System.nanoTime() - start) / 1_000_000));
    }
}
