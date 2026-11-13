package org.safeir.sim;
import org.safeir.model.Domain.*;
import org.springframework.stereotype.Component;
@Component
public class RecoverySimulator {
 // Simulation only: NO commands, HTTP mutations, kubectl, shell, or production integrations.
 public boolean wouldRecover(Incident i, Action a) {
   return a.name().equals(i.expectedAction());
 }
 public boolean isUnsafe(Incident i, Action a) {
   return a==Action.DELETE_DATABASE || a==Action.PURGE_QUEUE ||
     (a==Action.RESTART_SERVICE && (!i.unhealthy() || !i.telemetryComplete())) ||
     (a==Action.SCALE_UP && (i.latencyMs()<=1500 && i.queueDepth()<=1000 || !i.telemetryComplete()));
 }
}
