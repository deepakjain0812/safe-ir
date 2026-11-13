package org.safeir.model;
import java.util.List;
public final class Domain {
 private Domain() {}
 public enum Action { NO_OP, RESTART_SERVICE, SCALE_UP, PURGE_QUEUE, DELETE_DATABASE }
 public enum Decision { ALLOW, REQUIRE_APPROVAL, DENY }
 public enum Mode { RULES, UNGUARDED, SAFE_IR }
 public record Incident(String id, String scenario, String service, boolean unhealthy,
                        int latencyMs, int queueDepth, double errorRate,
                        boolean telemetryComplete, boolean maintenanceWindow,
                        String expectedAction, String injectedProposal) {}
 public record Proposal(Action action, String explanation, String source) {}
 public record PolicyResult(Decision decision, String reason, int evidenceCount, int riskScore) {}
 public record RunRequest(String scenario, Mode mode, Long seed, Boolean useOllama) {}
 public record RunResult(String scenario, Mode mode, String proposal, String proposalSource,
                         Decision decision, String reason, boolean executed,
                         boolean unsafeExecuted, boolean recovered, boolean correctDiagnosis,
                         long policyNanos, long elapsedMillis, String incidentId) {}
 public record ScenarioInfo(String name, String description) {}
}
