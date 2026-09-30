package com.example.selfheal;

import com.example.selfheal.agent.SelfHealState;
import com.example.selfheal.dto.AgentRequest;
import com.example.selfheal.service.DockerExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SelfHealAgentIntegrationTest {

    private static final String PLAN_JSON =
            "{\"description\":\"reverse a string\",\"functions\":[],\"dependencies\":[],\"complexity_notes\":\"\"}";
    private static final String TESTS =
            "```python\nfrom solution import reverse_string\n\ndef test_reverse():\n    assert reverse_string('abc') == 'cba'\n```";
    private static final String SOLUTION =
            "```python\ndef reverse_string(s):\n    return s[::-1]\n```";
    private static final String DIAGNOSIS =
            "FAILING: reverse_string returns the wrong result when called repeatedly.";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private DockerExecutorService dockerExecutorService;

    @MockBean
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        AtomicInteger index = new AtomicInteger();
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenAnswer(invocation -> LLM_RESPONSES[index.getAndIncrement()]);
    }

    @Test
    void succeedsOnFirstAttempt() {
        LLM_RESPONSES = new String[]{PLAN_JSON, TESTS, SOLUTION};
        when(dockerExecutorService.execute(anyString(), anyString())).thenReturn("");

        ResponseEntity<Map<String, Object>> response = runAgent();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals(1, ((Number) response.getBody().get("attempts")).intValue());
    }

    @Test
    void succeedsOnSecondAttemptAfterFailure() {
        LLM_RESPONSES = new String[]{PLAN_JSON, TESTS, SOLUTION, DIAGNOSIS, SOLUTION};
        when(dockerExecutorService.execute(anyString(), anyString())).thenReturn("1 failed, 3 passed", "");

        ResponseEntity<Map<String, Object>> response = runAgent();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals(2, ((Number) response.getBody().get("attempts")).intValue());
    }

    @Test
    void failsGracefullyAfterMaxAttempts() {
        LLM_RESPONSES = new String[2 * SelfHealState.MAX_ATTEMPTS + 1];
        LLM_RESPONSES[0] = PLAN_JSON;
        LLM_RESPONSES[1] = TESTS;
        for (int i = 2; i < LLM_RESPONSES.length; i++) {
            LLM_RESPONSES[i] = (i % 2 == 0) ? SOLUTION : DIAGNOSIS;
        }
        when(dockerExecutorService.execute(anyString(), anyString())).thenReturn("1 failed, 3 passed");

        ResponseEntity<Map<String, Object>> response = runAgent();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().get("success"));
        assertEquals(SelfHealState.MAX_ATTEMPTS, ((Number) response.getBody().get("attempts")).intValue());
    }

    @Test
    void streamsNodeEventsAndFinalResult() {
        LLM_RESPONSES = new String[]{PLAN_JSON, TESTS, SOLUTION};
        when(dockerExecutorService.execute(anyString(), anyString())).thenReturn("");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/agent/run/stream",
                new AgentRequest("Write a function that reverses a string"),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("event:node"));
        assertTrue(response.getBody().contains("\"phase\""));
        assertTrue(response.getBody().contains("event:result"));
        assertTrue(response.getBody().contains("\"success\":true"));
    }

    private ResponseEntity<Map<String, Object>> runAgent() {
        return restTemplate.postForEntity(
                "/agent/run",
                new AgentRequest("Write a function that reverses a string"),
                (Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    private static String[] LLM_RESPONSES;
}