import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import Keycloak from 'keycloak-js';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  let keycloakStub: { authenticated: boolean; realmAccess?: { roles: string[] }; login: jasmine.Spy };
  let route: ActivatedRouteSnapshot;
  let state: RouterStateSnapshot;

  beforeEach(() => {
    keycloakStub = {
      authenticated: true,
      realmAccess: { roles: [] },
      login: jasmine.createSpy('login').and.resolveTo(undefined),
    };

    route = {} as ActivatedRouteSnapshot;
    state = { url: '/scraping' } as RouterStateSnapshot;

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    });
  });

  it('allows activation when the caller has the admin realm role', async () => {
    keycloakStub.realmAccess = { roles: ['admin'] };

    const result = await TestBed.runInInjectionContext(() => adminGuard(route, state));

    expect(result).toBeTruthy();
    expect(keycloakStub.login).not.toHaveBeenCalled();
  });

  it('redirects an authenticated non-admin to /listings without prompting login', async () => {
    keycloakStub.realmAccess = { roles: ['user'] };

    const result = await TestBed.runInInjectionContext(() => adminGuard(route, state));

    expect(result).not.toBe(true);
    expect(keycloakStub.login).not.toHaveBeenCalled();
  });
});
