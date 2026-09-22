package com.example.selfheal.controller;

import com.example.selfheal.agent.SelfHealState;
import com.example.selfheal.dto.AgentRequest;
import com.example.selfheal.dto.AgentResponse;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
public class AgentController {

    private final CompiledGraph<SelfHealState> compiledGraph;

    public AgentController(CompiledGraph<SelfHealState> compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    @PostMapping("/agent/run")
    public ResponseEntity<?> run(@RequestBody AgentRequest request) {
        try {
            Optional<SelfHealState> finalState =
                    compiledGraph.invoke(Map.of(SelfHealState.TASK, request.task()));

            if (finalState.isEmpty()) {
                return ResponseEntity.status(500)
                        .body(Map.of("error", "Agent execution produced no result"));
            }

            return ResponseEntity.ok(buildResponse(finalState.get()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private AgentResponse buildResponse(SelfHealState state) {
        String failureOutput = state.<String>value(SelfHealState.FAILURE_OUTPUT).orElse("");
        boolean success = failureOutput.isEmpty();
        int attempts = state.<Integer>value(SelfHealState.ATTEMPT).orElse(0);

        return new AgentResponse(
                success,
                attempts,
                state.<String>value(SelfHealState.CODE).orElse(""),
                state.<String>value(SelfHealState.TESTS).orElse(""),
                state.<String>value(SelfHealState.PLAN).orElse(""),
                failureOutput);
    }
}
