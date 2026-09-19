package com.example.selfheal.agent;

import com.example.selfheal.service.DockerExecutorService;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Optional;

public class ExecutorNode implements NodeAction<SelfHealState> {

    private final DockerExecutorService dockerExecutorService;

    public ExecutorNode(DockerExecutorService dockerExecutorService) {
        this.dockerExecutorService = dockerExecutorService;
    }

    @Override
    public Map<String, Object> apply(SelfHealState state) {
        Optional<String> code = state.value(SelfHealState.CODE);
        Optional<String> tests = state.value(SelfHealState.TESTS);

        String result = dockerExecutorService.execute(code.orElse(""), tests.orElse(""));

        return Map.of(SelfHealState.FAILURE_OUTPUT, result);
    }
}