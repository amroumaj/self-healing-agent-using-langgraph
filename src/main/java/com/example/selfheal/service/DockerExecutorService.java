package com.example.selfheal.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class DockerExecutorService {

    public String execute(String code, String tests) {
        Path tempDir = null;
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

            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes());

            if (process.exitValue() == 0) {
                return "";
            }

            return output;
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute tests in Docker", e);
        } finally {
            deleteRecursively(tempDir);
        }
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
