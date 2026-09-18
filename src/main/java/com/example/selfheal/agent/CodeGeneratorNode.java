package com.example.selfheal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CodeGeneratorNode implements NodeAction<SelfHealState> {

    private static final String SYSTEM_PROMPT =
            "You are an expert Python developer. Output ONLY Python code in a single code block. No explanations. No print statements. No test code.";

    private static final String USER_PROMPT_TEMPLATE =
            "Implement the following functions.\n\nPlan:\n%s\n\n" +
            "Tests that your code must pass:\n%s\n\n" +
            "Rules:\n" +
            "- The module must be importable as 'solution'\n" +
            "- Include all necessary imports at the top\n" +
            "- Handle all edge cases mentioned in the plan\n" +
            "- Only use standard library and these modules: %s\n" +
            "- Do NOT include test code or print statements";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeGeneratorNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(SelfHealState state) {
        Optional<String> plan = state.value(SelfHealState.PLAN);
        Optional<String> tests = state.value(SelfHealState.TESTS);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(USER_PROMPT_TEMPLATE.formatted(
                        plan.orElse(""),
                        tests.orElse(""),
                        extractDependencies(plan.orElse(""))))
                .call()
                .content();

        return Map.of(SelfHealState.CODE, CodeExtractor.extractCode(response));
    }

    private String extractDependencies(String planJson) {
        try {
            JsonNode node = objectMapper.readTree(planJson);
            JsonNode dependencies = node.get("dependencies");
            if (dependencies != null && dependencies.isArray()) {
                List<String> modules = new ArrayList<>();
                dependencies.forEach(dep -> modules.add(dep.asText()));
                return String.join(", ", modules);
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
