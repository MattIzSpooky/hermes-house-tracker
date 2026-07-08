import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import Keycloak from 'keycloak-js';
import { WatchesPageComponent } from './watches-page.component';
import { AgentTaskResponse } from '../../core/api.types';

describe('WatchesPageComponent', () => {
  let httpMock: HttpTestingController;
  let keycloakStub: { tokenParsed: Record<string, unknown> | undefined };

  const task: AgentTaskResponse = {
    id: 'task-1',
    type: 'WATCH',
    status: 'ACTIVE',
    userId: 'user-1',
    name: 'Utrecht 3-bed',
    schedule: '0 0 8 * * *',
    nextRunAt: '2026-07-09T08:00:00Z',
  };

  async function setup(roles: string[]) {
    keycloakStub = { tokenParsed: { realm_access: { roles } } };
    await TestBed.configureTestingModule({
      imports: [WatchesPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(WatchesPageComponent);
    fixture.detectChanges();
    httpMock.expectOne('/api/agent-tasks').flush([task]);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('reports isAdmin true for the admin realm role', async () => {
    const fixture = await setup(['admin']);
    expect(fixture.componentInstance.isAdmin).toBeTrue();
  });

  it('reports isAdmin false for a non-admin role', async () => {
    const fixture = await setup(['user']);
    expect(fixture.componentInstance.isAdmin).toBeFalse();
  });

  it('runNow sets queuedTaskId on success', async () => {
    const fixture = await setup(['admin']);

    fixture.componentInstance.runNow('task-1');
    expect(fixture.componentInstance.runningTaskId()).toBe('task-1');

    const req = httpMock.expectOne('/api/agent-tasks/task-1/run');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 202, statusText: 'Accepted' });

    expect(fixture.componentInstance.runningTaskId()).toBeNull();
    expect(fixture.componentInstance.queuedTaskId()).toBe('task-1');
    expect(fixture.componentInstance.runError()).toBeNull();
  });

  it('runNow sets runError on failure', async () => {
    const fixture = await setup(['admin']);

    fixture.componentInstance.runNow('task-1');
    const req = httpMock.expectOne('/api/agent-tasks/task-1/run');
    req.flush({ detail: 'Not authorized to access this agent task' }, { status: 403, statusText: 'Forbidden' });

    expect(fixture.componentInstance.runningTaskId()).toBeNull();
    expect(fixture.componentInstance.queuedTaskId()).toBeNull();
    expect(fixture.componentInstance.runError()).toBe('Not authorized to access this agent task');
  });
});
