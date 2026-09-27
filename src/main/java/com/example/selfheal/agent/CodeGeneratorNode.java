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

    private static final String SYSTEM_PROMPT_FIRST =
            "You are an expert Python developer. Output ONLY Python code in a single code block. No explanations. No print statements. No test code.";

    private static final String USER_PROMPT_FIRST_TEMPLATE =
            "Implement the following functions.\n\nPlan:\n%s\n\n" +
            "Tests that your code must pass:\n%s\n\n" +
            "Rules:\n" +
            "- The module must be importable as 'solution'\n" +
            "- Include all necessary imports at the top\n" +
            "- Handle all edge cases mentioned in the plan\n" +
            "- Only use standard library and these modules: %s\n" +
            "- Do NOT include test code or print statements";

    private static final String SYSTEM_PROMPT_RETRY =
            "You are an expert Python developer debugging code. Output ONLY the COMPLETE corrected module in a single code block. No explanations.";

    private static final String USER_PROMPT_RETRY_TEMPLATE =
            "Your previous code:\n%s\n\n" +
            "Tests:\n%s\n\n" +
            "Failing output:\n%s\n\n" +
            "Diagnosis:\n%s\n\n" +
            "Fix ONLY the failing logic. Keep everything that already works. Output the COMPLETE corrected module in one code block.";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeGeneratorNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(SelfHealState state) {
        Optional<String> plan = state.value(SelfHealState.PLAN);
        Optional<String> tests = state.value(SelfHealState.TESTS);
        Optional<String> code = state.value(SelfHealState.CODE);
        Optional<String> failureOutput = state.value(SelfHealState.FAILURE_OUTPUT);
        Optional<String> diagnosis = state.value(SelfHealState.DIAGNOSIS);
        int attempt = state.<Integer>value(SelfHealState.ATTEMPT).orElse(0);

        String response;
        if (code.map(String::isBlank).orElse(true)) {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT_FIRST)
                    .user(USER_PROMPT_FIRST_TEMPLATE.formatted(
                            plan.orElse(""),
                            tests.orElse(""),
                            extractDependencies(plan.orElse(""))))
                    .call()
                    .content();
        } else {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT_RETRY)
                    .user(USER_PROMPT_RETRY_TEMPLATE.formatted(
                            code.orElse(""),
                            tests.orElse(""),
                            failureOutput.orElse(""),
                            diagnosis.orElse("")))
                    .call()
                    .content();
        }

        return Map.of(
                SelfHealState.CODE, CodeExtractor.extractCode(response),
                SelfHealState.ATTEMPT, attempt + 1);
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