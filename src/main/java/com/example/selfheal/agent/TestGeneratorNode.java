package com.example.selfheal.agent;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.Optional;

public class TestGeneratorNode implements NodeAction<SelfHealState> {

    private static final String SYSTEM_PROMPT =
            "You are a QA engineer. Output ONLY Python code in a single code block. No explanations.";

    private static final String USER_PROMPT_TEMPLATE =
            "Write pytest test cases for the following functions.\n\nPlan:\n%s\n\n" +
            "Rules:\n" +
            "- Import as: from solution import <function_name>\n" +
            "- At least 2 basic tests and 2 edge case tests per function\n" +
            "- Tests must be self-contained (no external files, no network calls)\n" +
            "- Use plain assert statements\n" +
            "- Output the complete test file in one code block";

    private final ChatClient chatClient;

    public TestGeneratorNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(SelfHealState state) {
        Optional<String> plan = state.value(SelfHealState.PLAN);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(USER_PROMPT_TEMPLATE.formatted(plan.orElse("")))
                .call()
                .content();

        return Map.of(SelfHealState.TESTS, CodeExtractor.extractCode(response));
    }
}