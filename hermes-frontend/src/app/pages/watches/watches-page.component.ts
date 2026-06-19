import { Component, inject, signal, OnInit } from '@angular/core';
import { AgentTaskService } from '../../core/agent-task.service';
import { AgentTaskResponse } from '../../core/api.types';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-watches-page',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './watches-page.component.html',
})
export class WatchesPageComponent implements OnInit {
  private readonly svc = inject(AgentTaskService);
  protected tasks = signal<AgentTaskResponse[]>([]);

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
}
