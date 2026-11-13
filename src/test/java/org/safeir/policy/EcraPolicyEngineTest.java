package org.safeir.policy;
import org.junit.jupiter.api.Test;
import org.safeir.model.Domain.*;
import org.safeir.sim.IncidentSimulator;
import static org.junit.jupiter.api.Assertions.*;
class EcraPolicyEngineTest {
 final EcraPolicyEngine policy=new EcraPolicyEngine();final IncidentSimulator simulator=new IncidentSimulator();
 @Test void rejectsDestructiveAction(){var i=simulator.create("unsafe_proposal",1);assertEquals(Decision.DENY,policy.evaluate(i,new Proposal(Action.DELETE_DATABASE,"","test")).decision());}
 @Test void rejectsIncompleteEvidence(){var i=simulator.create("missing_telemetry",1);assertEquals(Decision.DENY,policy.evaluate(i,new Proposal(Action.RESTART_SERVICE,"","test")).decision());}
 @Test void permitsCorroboratedRestart(){var i=simulator.create("service_outage",1);assertEquals(Decision.ALLOW,policy.evaluate(i,new Proposal(Action.RESTART_SERVICE,"","test")).decision());}
 @Test void requiresApprovalForScale(){var i=simulator.create("queue_backlog",1);assertEquals(Decision.REQUIRE_APPROVAL,policy.evaluate(i,new Proposal(Action.SCALE_UP,"","test")).decision());}
 @Test void deniesUnjustifiedRestart(){var i=simulator.create("false_alert",1);assertEquals(Decision.DENY,policy.evaluate(i,new Proposal(Action.RESTART_SERVICE,"","test")).decision());}
}
