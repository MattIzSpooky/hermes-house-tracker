import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgentTaskResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class AgentTaskService {
  private readonly http = inject(HttpClient);

  getTasks(): Observable<AgentTaskResponse[]> {
    return this.http.get<AgentTaskResponse[]>('/api/agent-tasks');
  }

  deleteTask(id: string): Observable<void> {
    return this.http.delete<void>(`/api/agent-tasks/${id}`);
  }

  runNow(id: string): Observable<void> {
    return this.http.post<void>(`/api/agent-tasks/${id}/run`, null);
  }
}
