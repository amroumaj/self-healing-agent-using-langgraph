package com.example.selfheal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerExecutorServiceTest {

    @Spy
    private DockerExecutorService service;

    private Process mockProcess(String output, int exitCode) throws Exception {
        Process process = mock(Process.class);
        doReturn(true).when(process).waitFor(90, TimeUnit.SECONDS);
        when(process.exitValue()).thenReturn(exitCode);
        when(process.getInputStream())
                .thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        return process;
    }

    @Test
    void returnsEmptyStringOnSuccess() throws Exception {
        Process process = mockProcess("2 passed in 0.01s", 0);
        doReturn(process).when(service).start(any(ProcessBuilder.class));

        String result = service.execute("code", "tests");

        assertEquals("", result);
    }

    @Test
    void returnsOutputOnFailure() throws Exception {
        String output = "1 failed, 1 passed";
        Process process = mockProcess(output, 1);
        doReturn(process).when(service).start(any(ProcessBuilder.class));

        String result = service.execute("code", "tests");

        assertEquals(output, result);
    }

    @Test
    void returnsTimeoutMessageOnTimeout() throws Exception {
        Process process = mock(Process.class);
        doReturn(false).when(process).waitFor(90, TimeUnit.SECONDS);
        doReturn(process).when(service).start(any(ProcessBuilder.class));

        String result = service.execute("code", "tests");

        assertTrue(result.startsWith("TIMEOUT"));
    }
}