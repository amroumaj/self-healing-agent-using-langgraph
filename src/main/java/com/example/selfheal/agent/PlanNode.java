package com.example.selfheal.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.Optional;

public class PlanNode implements NodeAction<SelfHealState> {

    private static final String SYSTEM_PROMPT =
            "You are a senior Python architect. Output ONLY valid JSON. No markdown fences. No explanations.";

    private static final String USER_PROMPT_TEMPLATE =
            "Break down this task into an implementation plan.\n\nTask: %s\n\n" +
            "Output a JSON object with these keys:\n" +
            "- description: one-line summary\n" +
            "- functions: array of objects with keys name, signature, description, edge_cases (array of strings)\n" +
            "- dependencies: array of python module names\n" +
            "- complexity_notes: string with tricky logic to watch out for";

    private static final String RETRY_SUFFIX =
            "\n\nIMPORTANT: Output ONLY raw JSON. No code fences. No markdown.";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PlanNode(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> apply(SelfHealState state) throws Exception {
        Optional<String> task = state.value(SelfHealState.TASK);

        String plan = callLlm(USER_PROMPT_TEMPLATE.formatted(task.orElse("")));

        if (!isValidJson(plan)) {
            plan = callLlm(USER_PROMPT_TEMPLATE.formatted(task.orElse("")) + RETRY_SUFFIX);
        }

        return Map.of(SelfHealState.PLAN, plan);
    }

    private String callLlm(String userPrompt) {
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        return CodeExtractor.extractCode(response);
    }

    private boolean isValidJson(String text) {
        try {
            objectMapper.readTree(text);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}