# Self-Healing Code Agent

A Spring Boot agent that uses LangGraph4j and a local Qwen model (via Ollama) to autonomously plan, write, test, and debug Python code in a Docker sandbox.

## How It Works
The agent receives a natural-language task, then loops:
1. Plans the implementation (function signatures, edge cases)
2. Generates pytest test cases
3. Writes the Python implementation
4. Runs tests in a Docker sandbox
5. If tests fail → diagnoses the root cause → rewrites the code
6. Repeats until all tests pass or 10 attempts are exhausted

## Prerequisites
- Java 21
- Maven 3.9+
- Docker
- Ollama

## Setup
1. docker compose up -d
2. ollama pull qwen2.5-coder:7b
3. docker pull python:3.12
4. mvn spring-boot:run

## Usage
curl -X POST http://localhost:8080/agent/run \
  -H "Content-Type: application/json" \
  -d '{"task": "Implement an in-memory key-value store with TTL support. Functions: set(key, value, ttl_seconds), get(key) returns None if expired, delete(key), keys() returns all non-expired keys. Use time.time() for expiry."}'
