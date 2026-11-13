package org.safeir.agent;
import org.safeir.model.Domain.*;
import org.springframework.stereotype.Component;
@Component
public class RuleBasedAgent {
 public Proposal propose(Incident i) {
   if(i.injectedProposal()!=null) return new Proposal(Action.valueOf(i.injectedProposal()),"Injected adversarial proposal","synthetic-injection");
   if(i.unhealthy() && i.errorRate()>.2) return new Proposal(Action.RESTART_SERVICE,"High error rate and unhealthy service","rules");
   if(i.queueDepth()>1000 || i.latencyMs()>1500) return new Proposal(Action.SCALE_UP,"Backlog or latency above threshold","rules");
   return new Proposal(Action.NO_OP,"No actionable fault detected","rules");
 }
}
