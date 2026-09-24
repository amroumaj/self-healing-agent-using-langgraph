package com.example.selfheal.controller;

import com.example.selfheal.agent.SelfHealState;
import com.example.selfheal.dto.AgentRequest;
import com.example.selfheal.dto.AgentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class AgentController {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private static final Map<String, String> NODE_PHASES = Map.of(
            StateGraph.START, "Starting self-heal agent",
            StateGraph.END, "Finished",
            "plan", "Planning the implementation",
            "generateTests", "Writing pytest tests",
            "generateCode", "Generating code",
            "execute", "Running tests in Docker",
            "diagnose", "Diagnosing test failure");

    private final CompiledGraph<SelfHealState> compiledGraph;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

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

    @PostMapping(value = "/agent/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AgentRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        streamExecutor.execute(() -> {
            try {
                AsyncGenerator<NodeOutput<SelfHealState>> generator =
                        compiledGraph.stream(Map.of(SelfHealState.TASK, request.task()));

                NodeOutput<SelfHealState> lastOutput = null;
                for (NodeOutput<SelfHealState> nodeOutput : generator) {
                    lastOutput = nodeOutput;
                    String nodeId = nodeOutput.node();
                    int attempt = nodeOutput.state().<Integer>value(SelfHealState.ATTEMPT).orElse(0);

                    String phase = NODE_PHASES.getOrDefault(nodeId, nodeId);
                    if ("generateCode".equals(nodeId)) {
                        phase = phase + " (attempt " + attempt + ")";
                    }

                    Map<String, Object> event = new HashMap<>();
                    event.put("node", nodeId);
                    event.put("phase", phase);
                    event.put("attempt", attempt);
                    event.put("state", nodeOutput.state().data());
                    emitter.send(SseEmitter.event()
                            .name("node")
                            .data(toJson(event), MediaType.APPLICATION_JSON));
                }

                if (lastOutput != null) {
                    emitter.send(SseEmitter.event()
                            .name("result")
                            .data(toJson(buildResponse(lastOutput.state())), MediaType.APPLICATION_JSON));
                }

                emitter.complete();
            } catch (Throwable t) {
                try {
                    Map<String, Object> error = Map.of("error", String.valueOf(t.getMessage()));
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(toJson(error), MediaType.APPLICATION_JSON));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });

        return emitter;
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

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    @PreDestroy
    void shutdownStreamExecutor() {
        streamExecutor.shutdown();
    }
}