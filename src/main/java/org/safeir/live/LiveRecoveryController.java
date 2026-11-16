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
    public record CaptureRequest(String service, Boolean useOllama, String testAction) {}
    public record ExecutionRequest(String captureId, boolean guarded) {}
    public record ApprovalDecisionRequest(String captureId, boolean approve, String reason) {}
    public record ApprovalRecord(String captureId, String service, String action, String proposalSource,
                                 Evidence evidence, Instant created, String status, String reviewerReason,
                                 Instant decidedAt) {}
    public record Evidence(String service, int samples, int failed, double errorRate,
                           int medianLatencyMs, boolean complete, boolean unhealthy) {}
    public record Captured(String id, String service, Proposal proposal, Instant created,
                           Set<String> arms) {}
    public record CaptureResponse(String captureId, String service, String action,
                                  String proposalSource, String proposalExplanation, Evidence evidence) {}
    public record ExecutionResponse(String captureId, String arm, String action,
                                    String decision, String reason, boolean applied,
                                    boolean verified, Evidence before, Evidence after,
                                    long elapsedMillis) {}

    private static final int SAMPLES = 5;
    @Value("${safeir.lab.capture-ttl:PT10M}")
    private Duration maxAge = Duration.ofMinutes(10);
    private static final Map<String, String> HOSTS = Map.of(
        "orders", "http://orders:8080",
        "payments", "http://payments:8080",
        "inventory", "http://inventory:8080");
    private final ConcurrentHashMap<String, Captured> captures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper;
    private final String token;
    private final RuleBasedAgent rules;
    private final OllamaAgent ollama;
    private final EcraPolicyEngine policy;
    @Value("${safeir.lab.test-proposals:false}")
    private boolean allowTestProposals;
    // Configure a separate reviewer credential explicitly; empty disables approvals.
    @Value("${safeir.lab.reviewer-token:}")
    private String reviewerToken = "";

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

    private boolean reviewerAuthorized(String supplied) {
        return reviewerToken != null && !reviewerToken.isBlank() && supplied != null
            && !reviewerToken.equals(token) && java.security.MessageDigest.isEqual(
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                reviewerToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        // Expired captures are never reusable; do not clear pending audit records.
        captures.entrySet().removeIf(e -> e.getValue().created().isBefore(Instant.now().minus(maxAge)));
        var evidence = sample(request.service());
        var incident = incident(evidence);
        Proposal proposal;
        try {
            if (request.testAction() != null) {
                if (!allowTestProposals) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Test proposal injection disabled"));
                proposal = new Proposal(Action.valueOf(request.testAction()), "Explicit lab adversarial test", "lab-test-injection");
            } else {
                proposal = Boolean.TRUE.equals(request.useOllama()) ? ollama.propose(incident) : rules.propose(incident);
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Proposal generation failed"));
        }
        String id = UUID.randomUUID().toString();
        captures.put(id, new Captured(id, request.service(), proposal, Instant.now(),
            ConcurrentHashMap.newKeySet()));
        return ResponseEntity.ok(new CaptureResponse(id, request.service(),
            proposal.action().name(), proposal.source(), proposal.explanation(), evidence));
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestHeader(value = "X-Lab-Token", required = false) String credential,
                                     @RequestBody ExecutionRequest request) {
        if (!authorized(credential)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.captureId() == null) return ResponseEntity.badRequest().build();
        Captured capture = captures.get(request.captureId());
        if (capture == null || capture.created().isBefore(Instant.now().minus(maxAge))) {
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
        if (request.guarded() && decision.decision() == Decision.REQUIRE_APPROVAL) {
            approvals.putIfAbsent(capture.id(), new ApprovalRecord(capture.id(), capture.service(),
                capture.proposal().action().name(), capture.proposal().source(), before,
                Instant.now(), "PENDING", null, null));
        }
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
    /** Lab reviewer API: separate credential; no approval can override policy or executor. */
    @GetMapping("/approval/{captureId}")
    public ResponseEntity<?> approval(@RequestHeader(value = "X-Reviewer-Token", required = false) String credential,
                                      @PathVariable String captureId) {
        if (!reviewerAuthorized(credential)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        ApprovalRecord record = approvals.get(captureId);
        if (record == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(record);
    }

    @PostMapping("/approval/decide")
    public ResponseEntity<?> decideApproval(
        @RequestHeader(value = "X-Reviewer-Token", required = false) String credential,
        @RequestBody ApprovalDecisionRequest request) {
        if (!reviewerAuthorized(credential)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.captureId() == null || request.reason() == null
            || request.reason().isBlank() || request.reason().length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "Capture ID and reviewer reason required (max 500 chars)"));
        }
        ApprovalRecord pending = approvals.get(request.captureId());
        Captured capture = captures.get(request.captureId());
        if (pending == null || capture == null) return ResponseEntity.notFound().build();
        if (!pending.status().equals("PENDING")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Approval already decided"));
        }
        if (capture.created().isBefore(Instant.now().minus(maxAge))) {
            ApprovalRecord expired = new ApprovalRecord(pending.captureId(), pending.service(),
                pending.action(), pending.proposalSource(), pending.evidence(), pending.created(),
                "EXPIRED", "Capture expired", Instant.now());
            approvals.replace(pending.captureId(), pending, expired);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Approval expired"));
        }
        // Reserve the decision atomically: concurrent reviewers cannot approve twice.
        ApprovalRecord reserved = new ApprovalRecord(pending.captureId(), pending.service(),
            pending.action(), pending.proposalSource(), pending.evidence(), pending.created(),
            "PROCESSING", request.reason(), Instant.now());
        if (!approvals.replace(pending.captureId(), pending, reserved)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Approval already decided"));
        }
        String status = request.approve() ? "APPROVED" : "REJECTED";
        String outcome = request.approve() ? "Action not supported by restricted lab executor" : "Rejected by reviewer";
        if (request.approve()) {
            Evidence fresh = sample(capture.service());
            PolicyResult reevaluated = policy.evaluate(incident(fresh), capture.proposal());
            // A reviewer may approve only while ECRA still requires approval.
            if (reevaluated.decision() != Decision.REQUIRE_APPROVAL) {
                status = "INVALIDATED";
                outcome = "Fresh evidence no longer requires approval: " + reevaluated.decision();
            }
            // No scaling or other unsupported mutation is performed here.
        }
        ApprovalRecord completed = new ApprovalRecord(reserved.captureId(), reserved.service(),
            reserved.action(), reserved.proposalSource(), reserved.evidence(), reserved.created(),
            status, request.reason(), Instant.now());
        approvals.replace(reserved.captureId(), reserved, completed);
        return ResponseEntity.ok(Map.of("approval", completed, "applied", false,
            "verified", false, "reason", outcome));
    }

}
