package com.example.selfheal.agent;

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

    private final ChatClient chatClient;

    public PlanNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(SelfHealState state) throws Exception {
        Optional<String> task = state.value(SelfHealState.TASK);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(USER_PROMPT_TEMPLATE.formatted(task.orElse("")))
                .call()
                .content();

        return Map.of(SelfHealState.PLAN, CodeExtractor.extractCode(response));
    }
}
