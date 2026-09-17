package com.example.selfheal.config;

import com.example.selfheal.agent.PlanNode;
import com.example.selfheal.agent.SelfHealState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@Configuration
public class AgentConfig {

    @Bean
    public ChatClient chatClient(ObjectProvider<org.springframework.ai.chat.client.ChatClient.Builder> builderProvider) {
        org.springframework.ai.chat.client.ChatClient.Builder builder = builderProvider.getIfAvailable();
        if (builder == null) {
            return null;
        }
        return builder.build();
    }

    @Bean
    public CompiledGraph<SelfHealState> selfHealGraph(ChatClient chatClient) throws Exception {
        StateGraph<SelfHealState> graph = new StateGraph<>(SelfHealState.schema(), SelfHealState::new);

        graph.addNode("plan", AsyncNodeAction.node_async(new PlanNode(chatClient)));

        graph.addEdge(START, "plan");
        graph.addEdge("plan", END);

        return graph.compile();
    }
}
