package com.example.selfheal.agent;

import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class GraphStructureTest {

    @Autowired
    private CompiledGraph<SelfHealState> selfHealGraph;

    @MockBean
    private ChatClient chatClient;

    @Test
    void graphBeanIsPresent() {
        assertNotNull(selfHealGraph);
    }
}