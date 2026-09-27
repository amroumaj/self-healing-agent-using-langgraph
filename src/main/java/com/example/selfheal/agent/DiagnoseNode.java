package com.example.selfheal.agent;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.Optional;

public class DiagnoseNode implements NodeAction<SelfHealState> {

    private static final String SYSTEM_PROMPT =
            "You are a debugging expert. Be precise and actionable. No filler.";

    private static final String USER_PROMPT_TEMPLATE =
            "Analyze why these tests failed.\n\n" +
            "Code:\n%s\n\n" +
            "Tests:\n%s\n\n" +
            "Pytest output:\n%s\n\n" +
            "For each failing test explain:\n" +
            "1. What the test expected\n" +
            "2. What actually happened\n" +
            "3. The root cause in the code\n" +
            "4. The specific line(s) or logic that need to change";

    private final ChatClient chatClient;

    public DiagnoseNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(SelfHealState state) {
        Optional<String> code = state.value(SelfHealState.CODE);
        Optional<String> tests = state.value(SelfHealState.TESTS);
        Optional<String> failureOutput = state.value(SelfHealState.FAILURE_OUTPUT);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(USER_PROMPT_TEMPLATE.formatted(
                        code.orElse(""),
                        tests.orElse(""),
                        failureOutput.orElse("")))
                .call()
                .content();

        return Map.of(SelfHealState.DIAGNOSIS, response.trim());
    }
}