package com.example.selfheal.agent;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.Map;

public class SelfHealState extends AgentState {

    public static final int MAX_ATTEMPTS = 10;

    public static final String TASK = "task";
    public static final String PLAN = "plan";
    public static final String TESTS = "tests";
    public static final String CODE = "code";
    public static final String FAILURE_OUTPUT = "failureOutput";
    public static final String DIAGNOSIS = "diagnosis";
    public static final String ATTEMPT = "attempt";

    public SelfHealState(Map<String, Object> initData) {
        super(initData);
    }

    public static Map<String, Channel<?>> schema() {
        return Map.of(
                TASK, Channels.base(() -> ""),
                PLAN, Channels.base(() -> ""),
                TESTS, Channels.base(() -> ""),
                CODE, Channels.base(() -> ""),
                FAILURE_OUTPUT, Channels.base(() -> ""),
                DIAGNOSIS, Channels.base(() -> ""),
                ATTEMPT, Channels.base(() -> 0)
        );
    }
}