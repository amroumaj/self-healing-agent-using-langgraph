import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AgentErrorEvent,
  AgentNodeEvent,
  AgentResultEvent,
  AgentService,
} from './agent.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly task = signal('');
  protected readonly running = signal(false);
  protected readonly currentPhase = signal('');
  protected readonly steps = signal<AgentNodeEvent[]>([]);
  protected readonly result = signal<AgentResultEvent | null>(null);
  protected readonly error = signal('');
  protected readonly copied = signal(false);

  private abort?: AbortController;
  private copyTimer?: ReturnType<typeof setTimeout>;

  constructor(private readonly agent: AgentService) {}

  protected run(): void {
    const task = this.task().trim();
    if (this.running() || !task) return;

    this.abort?.abort();
    this.abort = new AbortController();
    this.running.set(true);
    this.steps.set([]);
    this.result.set(null);
    this.error.set('');

    this.agent.stream(task, this.abort.signal).subscribe({
      next: (ev: AgentNodeEvent | AgentResultEvent | AgentErrorEvent) => {
        if (ev.type === 'node') {
          this.steps.update((steps) => [...steps, ev]);
          this.currentPhase.set(ev.phase);
        } else if (ev.type === 'result') {
          this.result.set(ev);
          this.currentPhase.set('');
        } else {
          this.error.set(ev.message);
        }
      },
      complete: () => this.running.set(false),
      error: (e: unknown) => {
        this.error.set(e instanceof Error ? e.message : String(e));
        this.running.set(false);
      },
    });
  }

  protected isExecuteOk(step: AgentNodeEvent): boolean {
    return step.node === 'execute' && !step.state['failureOutput'];
  }

  protected isExecuteFail(step: AgentNodeEvent): boolean {
    return step.node === 'execute' && !!step.state['failureOutput'];
  }

  protected hasKey(step: AgentNodeEvent, key: string): boolean {
    return !!step.state[key];
  }

  protected copy(text: string): void {
    if (!text) return;
    this.copied.set(true);
    clearTimeout(this.copyTimer);
    this.copyTimer = setTimeout(() => this.copied.set(false), 1600);
    navigator.clipboard?.writeText(text).catch(() => undefined);
  }
}