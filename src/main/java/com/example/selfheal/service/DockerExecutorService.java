package com.example.selfheal.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class DockerExecutorService {

    private static final long TIMEOUT_SECONDS = 90;
    private static final String TIMEOUT_MESSAGE = "TIMEOUT: test execution exceeded " + TIMEOUT_SECONDS + " seconds";

    public String execute(String code, String tests) {
        Path tempDir = null;
        Process process = null;
        try {
            tempDir = Files.createTempDirectory("selfheal-");
            Files.writeString(tempDir.resolve("solution.py"), code);
            Files.writeString(tempDir.resolve("test_solution.py"), tests);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", tempDir + ":/app",
                    "-w", "/app",
                    "python:3.12",
                    "sh", "-c",
                    "pip install --quiet --disable-pip-version-check pytest >/dev/null 2>&1 && python -m pytest test_solution.py -v --tb=short");
            processBuilder.redirectErrorStream(true);

            process = start(processBuilder);

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy();
                return TIMEOUT_MESSAGE;
            }

            String output = new String(process.getInputStream().readAllBytes());

            if (process.exitValue() == 0) {
                return "";
            }

            if (output.contains("Cannot connect to the Docker daemon")) {
                throw new RuntimeException("Docker daemon is not running. Start Docker and try again.");
            }
            if (output.contains("No such image") || output.contains("pull access denied")) {
                throw new RuntimeException("python:3.12 image not found. Run: docker pull python:3.12");
            }

            return output;
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute tests in Docker", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test execution interrupted", e);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            deleteRecursively(tempDir);
        }
    }

    Process start(ProcessBuilder processBuilder) throws IOException {
        return processBuilder.start();
    }

    private void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}