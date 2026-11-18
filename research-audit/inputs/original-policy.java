package org.safeir.policy;
import org.safeir.model.Domain.*;
import org.springframework.stereotype.Component;
@Component
public class EcraPolicyEngine {
 public PolicyResult evaluate(Incident i,Proposal p) {
   Action a=p.action();
   int evidence=(i.telemetryComplete()?1:0)+(i.unhealthy()?1:0)+(i.errorRate()>.2?1:0)+
     (i.latencyMs()>1500?1:0)+(i.queueDepth()>1000?1:0);
   int risk=switch(a){case NO_OP->0;case RESTART_SERVICE->2;case SCALE_UP->3;case PURGE_QUEUE->8;case DELETE_DATABASE->10;};
   if(a==Action.NO_OP) return new PolicyResult(Decision.ALLOW,"No operational mutation",evidence,risk);
   if(a==Action.DELETE_DATABASE || a==Action.PURGE_QUEUE) return new PolicyResult(Decision.DENY,"Destructive action prohibited",evidence,risk);
   if(!i.telemetryComplete()) return new PolicyResult(Decision.DENY,"Telemetry incomplete",evidence,risk);
   if(a==Action.RESTART_SERVICE && (!i.unhealthy() || i.errorRate()<=.2))
      return new PolicyResult(Decision.DENY,"Restart lacks corroborating health and error evidence",evidence,risk);
   if(a==Action.SCALE_UP && i.latencyMs()<=1500 && i.queueDepth()<=1000)
      return new PolicyResult(Decision.DENY,"Scaling lacks corroborating latency or queue evidence",evidence,risk);
   if(i.maintenanceWindow() || risk>=3) return new PolicyResult(Decision.REQUIRE_APPROVAL,"Human approval required",evidence,risk);
   return new PolicyResult(Decision.ALLOW,"Allowlisted action with sufficient evidence",evidence,risk);
 }
}
