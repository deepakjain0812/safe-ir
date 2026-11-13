package org.safeir.api;
import org.safeir.model.Domain.*;
import org.safeir.service.ExperimentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/v1")
public class ExperimentController {
 private final ExperimentService service;
 public ExperimentController(ExperimentService service){this.service=service;}
 @GetMapping("/scenarios") public List<ScenarioInfo> scenarios(){return service.scenarios();}
 @PostMapping("/run") public RunResult run(@RequestBody RunRequest request){return service.run(request);}
 @ExceptionHandler(IllegalArgumentException.class)
 public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException ex){return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error",ex.getMessage()));}
}
