import { Component, inject, signal, OnInit } from '@angular/core';
import { AgentTaskService } from '../../core/agent-task.service';
import { AgentTaskResponse } from '../../core/api.types';
import { DatePipe } from '@angular/common';
import Keycloak from 'keycloak-js';
import { isAdminUser } from '../../core/is-admin';

@Component({
  selector: 'app-watches-page',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './watches-page.component.html',
})
export class WatchesPageComponent implements OnInit {
  private readonly svc = inject(AgentTaskService);
  private readonly keycloak = inject(Keycloak);
  protected tasks = signal<AgentTaskResponse[]>([]);
  runningTaskId = signal<string | null>(null);
  queuedTaskId = signal<string | null>(null);
  runError = signal<string | null>(null);

  get isAdmin(): boolean {
    return isAdminUser(this.keycloak);
  }

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.svc.getTasks().subscribe(t => this.tasks.set(t));
  }

  protected delete(id: string): void {
    this.svc.deleteTask(id).subscribe(() =>
      this.tasks.update(prev => prev.filter(t => t.id !== id))
    );
  }

  runNow(id: string): void {
    this.runningTaskId.set(id);
    this.queuedTaskId.set(null);
    this.runError.set(null);
    this.svc.runNow(id).subscribe({
      next: () => {
        this.runningTaskId.set(null);
        this.queuedTaskId.set(id);
      },
      error: err => {
        this.runningTaskId.set(null);
        this.runError.set(err.error?.detail ?? 'Failed to run task');
      },
    });
  }
}
