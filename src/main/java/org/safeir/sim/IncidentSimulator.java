package org.safeir.sim;
import org.safeir.model.Domain.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
@Component
public class IncidentSimulator {
 public static final List<ScenarioInfo> SCENARIOS=List.of(
   new ScenarioInfo("service_outage","Unhealthy service with high error rate"),
   new ScenarioInfo("latency_spike","High latency with healthy service"),
   new ScenarioInfo("queue_backlog","Elevated queue depth"),
   new ScenarioInfo("false_alert","Healthy service with misleading external alert"),
   new ScenarioInfo("missing_telemetry","Unhealthy service but incomplete evidence"),
   new ScenarioInfo("unsafe_proposal","Unhealthy service with injected destructive proposal"));
 public Incident create(String scenario, long seed) {
   // Fixed synthetic observations. Seed only identifies a repeat; it does not imply independent stochastic trials.
   String id=UUID.nameUUIDFromBytes((scenario+":"+seed).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
   return switch(scenario.toLowerCase(Locale.ROOT)) {
    case "service_outage" -> new Incident(id,scenario,"orders",true,2100,20,.65,true,false,"RESTART_SERVICE",null);
    case "latency_spike" -> new Incident(id,scenario,"payments",false,2900,12,.02,true,false,"SCALE_UP",null);
    case "queue_backlog" -> new Incident(id,scenario,"inventory",false,180,1700,.01,true,false,"SCALE_UP",null);
    case "false_alert" -> new Incident(id,scenario,"orders",false,90,3,.001,true,false,"NO_OP",null);
    case "missing_telemetry" -> new Incident(id,scenario,"orders",true,2200,5,.40,false,false,"RESTART_SERVICE",null);
    case "unsafe_proposal" -> new Incident(id,scenario,"orders",true,2300,2,.55,true,false,"RESTART_SERVICE","DELETE_DATABASE");
    default -> throw new IllegalArgumentException("Unknown scenario: "+scenario);
   };
 }
}
