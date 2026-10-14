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

## Architecture

The agent is a state machine built with LangGraph4j (`StateGraph<SelfHealState>`), defined in `AgentConfig`.

### Graph
- `plan` → `generateTests` → `generateCode` → `execute`
- `execute` → `diagnose` → `generateCode` (self-healing loop)
- `execute` → END when tests pass or `attempt >= MAX_ATTEMPTS` (10)

### Nodes
| Node | Responsibility |
|------|----------------|
| `PlanNode` | Breaks the task into a JSON plan (functions, edge cases, dependencies) |
| `TestGeneratorNode` | Writes pytest test cases from the plan |
| `CodeGeneratorNode` | Writes the Python module (`solution.py`); on retry, rewrites it given the failing output and diagnosis |
| `ExecutorNode` | Runs the tests in `python:3.12` via Docker, returns failure output or empty on success |
| `DiagnoseNode` | Explains root causes of test failures to guide the next rewrite |

### State
`SelfHealState` persists across graph steps through channels: `task`, `plan`, `tests`, `code`, `failureOutput`, `diagnosis`, and `attempt` (all with sensible defaults).

### Packages
- `config` — graph wiring and bean definitions
- `agent` — graph nodes and shared state
- `service` — Docker test sandbox execution
- `controller` — HTTP entrypoints (`POST /agent/run`, `POST /agent/run/stream`)
- `dto` — request/response records

### Infrastructure
- **Ollama** serves `qwen2.5-coder:7b` locally at `localhost:11434` (configurable in `src/main/resources/application.yml` — pull a larger tag such as `qwen2.5-coder:14b` and set `spring.ai.ollama.chat.options.model` to use it)
- **Docker** runs `python:3.12` with the generated code and tests mounted into `/app`, isolated from the host
- Everything runs locally — no cloud services, no external APIs

### API Response
`POST /agent/run` returns:

```json
{
  "success": true,
  "attempts": 2,
  "code": "...",
  "tests": "...",
  "plan": "...",
  "finalOutput": ""
}
```

### Streaming
`POST /agent/run/stream` returns `text/event-stream` so you can watch the agent think in real time:

```
curl -N -X POST http://localhost:8080/agent/run/stream \
  -H "Content-Type: application/json" \
  -d '{"task": "Implement fibonacci"}'
```

Each step emits an `event:node` frame with the node id, a human-readable `phase` (e.g. `"Planning the implementation"`, `"Generating code (attempt 2)"`), the attempt number, and a full state snapshot (`plan`, `tests`, `code`, `failureOutput`, ...), followed by a final `event:result` frame with the same payload as `/agent/run`, or an `event:error` frame on failure.

## Clients

Two interactive ways to watch the agent work:

### Terminal (`client/agent_cli.py`)

Zero-dependency Python (stdlib only) SSE viewer that prints each phase live:

```bash
python3 client/agent_cli.py "Implement a thread-safe LRU cache with get, put, size, clear, eviction, and a hits counter."
echo "Implement fibonacci" | python3 client/agent_cli.py
python3 client/agent_cli.py --plain "Implement fibonacci"   # non-streaming summary
python3 client/agent_cli.py --url http://192.168.1.5:8080/agent/run/stream "..."
```

It colors pass/fail per attempt: green `✓ Running tests in Docker — all tests passed`, red `✗ ... — <failing assert>` when a rewrite is triggered, plus the final result and code.

### Angular frontend (`frontend/`)

Angular 22 app that renders the stream as a live phase ticker and a per-step timeline with expandable plan/tests/code/diagnosis panes:

```bash
cd frontend
npm install
npm start        # http://localhost:4200, proxies /agent → localhost:8080
```

`ng serve` proxies `/agent/**` to the backend (see `proxy.conf.json`), so no CORS setup is needed in dev; the backend also allows `localhost:4200` directly via CORS. Production build: `npm run build` → `frontend/dist/frontend`.