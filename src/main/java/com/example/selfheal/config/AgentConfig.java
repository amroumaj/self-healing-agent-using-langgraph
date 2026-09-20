package com.example.selfheal.config;

import com.example.selfheal.agent.CodeGeneratorNode;
import com.example.selfheal.agent.ExecutorNode;
import com.example.selfheal.agent.PlanNode;
import com.example.selfheal.agent.SelfHealState;
import com.example.selfheal.agent.TestGeneratorNode;
import com.example.selfheal.service.DockerExecutorService;
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
    public CompiledGraph<SelfHealState> selfHealGraph(ChatClient chatClient,
                                                      DockerExecutorService dockerExecutorService) throws Exception {
        StateGraph<SelfHealState> graph = new StateGraph<>(SelfHealState.schema(), SelfHealState::new);

        graph.addNode("plan", AsyncNodeAction.node_async(new PlanNode(chatClient)));
        graph.addNode("generateTests", AsyncNodeAction.node_async(new TestGeneratorNode(chatClient)));
        graph.addNode("generateCode", AsyncNodeAction.node_async(new CodeGeneratorNode(chatClient)));
        graph.addNode("execute", AsyncNodeAction.node_async(new ExecutorNode(dockerExecutorService)));

        graph.addEdge(START, "plan");
        graph.addEdge("plan", "generateTests");
        graph.addEdge("generateTests", "generateCode");
        graph.addEdge("generateCode", "execute");
        graph.addEdge("execute", END);

        return graph.compile();
    }
}
