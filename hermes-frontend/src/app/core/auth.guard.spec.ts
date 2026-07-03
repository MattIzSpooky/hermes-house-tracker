import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import Keycloak from 'keycloak-js';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let keycloakStub: { authenticated: boolean; login: jasmine.Spy };
  let route: ActivatedRouteSnapshot;
  let state: RouterStateSnapshot;

  beforeEach(() => {
    keycloakStub = {
      authenticated: false,
      login: jasmine.createSpy('login').and.resolveTo(undefined),
    };

    route = {} as ActivatedRouteSnapshot;
    state = { url: '/watches' } as RouterStateSnapshot;

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    });
  });

  it('allows activation and does not call login when authenticated', async () => {
    keycloakStub.authenticated = true;

    const result = await TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(result).toBeTruthy();
    expect(keycloakStub.login).not.toHaveBeenCalled();
  });

  it('redirects to login and denies activation when not authenticated', async () => {
    keycloakStub.authenticated = false;

    const result = await TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(result).toBeFalsy();
    expect(keycloakStub.login).toHaveBeenCalledTimes(1);
    const loginArgs = keycloakStub.login.calls.mostRecent().args[0];
    expect(loginArgs.redirectUri).toContain('/watches');
  });
});
