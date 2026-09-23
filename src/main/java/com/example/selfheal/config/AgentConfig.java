package com.example.selfheal.config;

import com.example.selfheal.agent.CodeGeneratorNode;
import com.example.selfheal.agent.DiagnoseNode;
import com.example.selfheal.agent.ExecutorNode;
import com.example.selfheal.agent.PlanNode;
import com.example.selfheal.agent.SelfHealState;
import com.example.selfheal.agent.TestGeneratorNode;
import com.example.selfheal.service.DockerExecutorService;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Optional;

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
        graph.addNode("diagnose", AsyncNodeAction.node_async(new DiagnoseNode(chatClient)));

        graph.addEdge(START, "plan");
        graph.addEdge("plan", "generateTests");
        graph.addEdge("generateTests", "generateCode");
        graph.addEdge("generateCode", "execute");

        EdgeAction<SelfHealState> shouldContinue = state -> {
            Optional<String> failureOutput = state.value(SelfHealState.FAILURE_OUTPUT);
            int attempt = state.<Integer>value(SelfHealState.ATTEMPT).orElse(0);
            if (failureOutput.map(String::isEmpty).orElse(true) || attempt >= SelfHealState.MAX_ATTEMPTS) {
                return "end";
            }
            return "diagnose";
        };

        graph.addConditionalEdges("execute",
                AsyncEdgeAction.edge_async(shouldContinue),
                Map.of("end", END, "diagnose", "diagnose"));

        graph.addEdge("diagnose", "generateCode");

        CompiledGraph<SelfHealState> compiled = graph.compile();
        compiled.setMaxIterations(SelfHealState.MAX_ATTEMPTS * 4 + 10);
        return compiled;
    }
}