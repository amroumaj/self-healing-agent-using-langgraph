#!/usr/bin/env python3
"""Terminal viewer for the self-heal agent.

Watches the SSE stream from POST /agent/run/stream and prints each phase
the agent goes through, or hits POST /agent/run for a plain summary.

Usage:
    ./agent_cli.py "Implement a fibonacci function"
    echo "Implement a fibonacci function" | ./agent_cli.py
    ./agent_cli.py --plain "Implement a fibonacci function"
    ./agent_cli.py --url http://192.168.1.5:8080/agent/run/stream "..."
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

COLORS = {
    "reset": "\033[0m",
    "dim": "\033[2m",
    "green": "\033[32m",
    "red": "\033[31m",
    "cyan": "\033[36m",
    "yellow": "\033[33m",
}

MAX_PREVIEW = 500


def color(code, text):
    if not sys.stdout.isatty():
        return text
    return f"{COLORS[code]}{text}{COLORS['reset']}"


def post(url, payload):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    return urllib.request.urlopen(request)


def sse_frames(stream):
    frame = []
    for raw in stream:
        for line in raw.decode("utf-8", "replace").splitlines():
            if line == "":
                if frame:
                    yield frame
                    frame = []
            else:
                frame.append(line)
    if frame:
        yield frame


def parse_frame(lines):
    event, data_lines = None, []
    for line in lines:
        if line.startswith(":"):
            continue
        field, _, value = line.partition(":")
        if value.startswith(" "):
            value = value[1:]
        if field == "event":
            event = value
        elif field == "data":
            data_lines.append(value)
    return event, "\n".join(data_lines)


def render_node(payload):
    node = payload.get("node")
    phase = payload.get("phase")
    state = payload.get("state", {})
    attempt = payload.get("attempt", 0)

    if node == "__START__":
        print(color("dim", "starting agent…"))
        return
    if node == "__END__":
        print(color("dim", "finished graph traversal"))
        return

    label = phase or node
    if node == "generateCode":
        print(color("cyan", f"▶ {label}"))
    elif node == "execute":
        failure = state.get("failureOutput", "") or ""
        if failure:
            head = failure.strip().splitlines()
            preview = head[0] if head else failure
            if len(preview) > MAX_PREVIEW:
                preview = preview[:MAX_PREVIEW] + "…"
            print(color("red", f"✗ {label} — {preview}"))
        else:
            print(color("green", f"✓ {label} — all tests passed"))
    else:
        print(f"▶ {label}")


def render_result(payload, state):
    success = bool(payload.get("success"))
    attempts = payload.get("attempts", 0)
    if success:
        print(color("green", f"\n✓ success in {attempts} attempt(s)"))
    else:
        print(color("red", f"\n✗ failed after {attempts} attempt(s)"))
    print(color("dim", "code:"))
    print(state.get("code", ""))
    if state.get("failureOutput"):
        print(color("dim", "last failure output:"))
        print(state["failureOutput"])


def run_stream(url, task):
    body = post(url, {"task": task})
    last = None
    try:
        with body as resp:
            for lines in sse_frames(resp):
                event, data = parse_frame(lines)
                if event == "node":
                    payload = json.loads(data)
                    render_node(payload)
                elif event == "result":
                    payload = json.loads(data)
                    last = payload
        if last is not None:
            render_result(last, {"code": last.get("code", ""), "failureOutput": last.get("finalOutput", "")})
    except KeyboardInterrupt:
        print("\ninterrupted")
        return 130
    return 0


def run_plain(url, task):
    try:
        with post(url, {"task": task}) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        state = {
            "code": payload.get("code", ""),
            "failureOutput": "" if payload.get("success") else payload.get("finalOutput", ""),
        }
        render_result(payload, state)
        return 0
    except urllib.error.HTTPError as e:
        print(color("red", f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')}"))
        return 1


def main():
    ap = argparse.ArgumentParser(description="Watch the self-heal agent work.")
    ap.add_argument("task", nargs="*", help="task text (or pipe it via stdin)")
    ap.add_argument("--url", default="http://localhost:8080/agent/run/stream",
                    help="stream endpoint (default: http://localhost:8080/agent/run/stream)")
    ap.add_argument("--plain", action="store_true", help="use /agent/run instead of the stream")
    args = ap.parse_args()

    task = " ".join(args.task) if args.task else sys.stdin.read().strip()
    if not task:
        ap.error("no task provided")

    plain_url = args.url.replace("/stream", "") if "/stream" in args.url else args.url
    if args.plain:
        return run_plain(plain_url, task)
    try:
        return run_stream(args.url, task)
    except urllib.error.HTTPError as e:
        print(color("red", f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')}"))
        return 1
    except (urllib.error.URLError, ConnectionError) as e:
        print(color("red", f"cannot reach {args.url}: {e}"))
        return 1


if __name__ == "__main__":
    sys.exit(main())