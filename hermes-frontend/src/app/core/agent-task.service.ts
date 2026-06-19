import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgentTaskResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class AgentTaskService {
  private readonly http = inject(HttpClient);
  private readonly clientId = localStorage.getItem('hermes-chat-session') ?? '';

  getTasks(): Observable<AgentTaskResponse[]> {
    return this.http.get<AgentTaskResponse[]>(`/api/agent-tasks?clientId=${this.clientId}`);
  }

  deleteTask(id: string): Observable<void> {
    return this.http.delete<void>(`/api/agent-tasks/${id}`);
  }
}
