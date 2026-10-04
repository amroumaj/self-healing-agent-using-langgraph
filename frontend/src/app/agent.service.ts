import { Injectable } from '@angular/core';
import { Observable, Observer } from 'rxjs';

export interface AgentNodeEvent {
  type: 'node';
  node: string;
  phase: string;
  attempt: number;
  state: Record<string, string>;
}

export interface AgentResultEvent {
  type: 'result';
  success: boolean;
  attempts: number;
  code: string;
  tests: string;
  plan: string;
  finalOutput: string;
}

export interface AgentErrorEvent {
  type: 'error';
  message: string;
}

export type AgentEvent = AgentNodeEvent | AgentResultEvent | AgentErrorEvent;

interface StreamNodePayload {
  node: string;
  phase?: string;
  attempt?: number;
  state?: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class AgentService {
  stream(task: string, signal?: AbortSignal): Observable<AgentEvent> {
    return new Observable((observer: Observer<AgentEvent>) => {
      fetch('/agent/run/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ task }),
        signal,
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            throw new Error(`HTTP ${response.status}`);
          }

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';

          const handleFrame = (frame: string) => {
            let event = 'message';
            let data = '';
            for (const line of frame.split('\n')) {
              if (line.startsWith(':')) continue;
              const idx = line.indexOf(':');
              if (idx < 0) continue;
              const field = line.slice(0, idx);
              const value = line.slice(idx + 1).replace(/^ /, '');
              if (field === 'event') event = value;
              else if (field === 'data') data += `${value}\n`;
            }
            if (!data.trim()) return;

            const payload: StreamNodePayload & {
              success?: boolean;
              attempts?: number;
              code?: string;
              tests?: string;
              plan?: string;
              finalOutput?: string;
              error?: string;
            } = JSON.parse(data);

            if (event === 'node') {
              observer.next({
                type: 'node',
                node: payload.node,
                phase: payload.phase ?? payload.node,
                attempt: payload.attempt ?? 0,
                state: payload.state ?? {},
              });
            } else if (event === 'result') {
              observer.next({
                type: 'result',
                success: payload.success ?? false,
                attempts: payload.attempts ?? 0,
                code: payload.code ?? '',
                tests: payload.tests ?? '',
                plan: payload.plan ?? '',
                finalOutput: payload.finalOutput ?? '',
              });
            } else if (event === 'error') {
              observer.next({ type: 'error', message: payload.error ?? 'unknown error' });
            }
          };

          for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            let sep: number;
            while ((sep = buffer.indexOf('\n\n')) >= 0) {
              const frame = buffer.slice(0, sep);
              buffer = buffer.slice(sep + 2);
              if (frame.trim()) handleFrame(frame);
            }
          }
          observer.complete();
        })
        .catch((err) => {
          if (err?.name === 'AbortError') {
            observer.complete();
          } else {
            observer.error(err);
          }
        });
    });
  }
}