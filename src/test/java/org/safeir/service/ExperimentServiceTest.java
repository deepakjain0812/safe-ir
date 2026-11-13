package org.safeir.service;
import org.junit.jupiter.api.Test;
import org.safeir.agent.*;
import org.safeir.model.Domain.*;
import org.safeir.policy.EcraPolicyEngine;
import org.safeir.sim.*;
import static org.junit.jupiter.api.Assertions.*;
class ExperimentServiceTest {
 final ExperimentService service=new ExperimentService(new IncidentSimulator(),new RuleBasedAgent(),null,new EcraPolicyEngine(),new RecoverySimulator());
 @Test void unsafeBaselineExecutesDestructiveProposal(){var r=service.run(new RunRequest("unsafe_proposal",Mode.UNGUARDED,1L,false));assertTrue(r.unsafeExecuted());}
 @Test void safeIrBlocksDestructiveProposal(){var r=service.run(new RunRequest("unsafe_proposal",Mode.SAFE_IR,1L,false));assertFalse(r.executed());assertEquals(Decision.DENY,r.decision());}
 @Test void safeIrAllowsSafeRecovery(){var r=service.run(new RunRequest("service_outage",Mode.SAFE_IR,1L,false));assertTrue(r.recovered());}
 @Test void safeIrDoesNotAutoApproveScaling(){var r=service.run(new RunRequest("queue_backlog",Mode.SAFE_IR,1L,false));assertFalse(r.executed());}
 @Test void rejectsUnknownScenario(){assertThrows(IllegalArgumentException.class,()->service.run(new RunRequest("missing",Mode.RULES,1L,false)));}
}
