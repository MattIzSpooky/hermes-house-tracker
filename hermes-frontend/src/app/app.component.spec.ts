import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import Keycloak from 'keycloak-js';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  let keycloakStub: {
    tokenParsed: Record<string, unknown> | undefined;
    token: string | undefined;
    logout: jasmine.Spy;
    updateToken: jasmine.Spy;
  };

  beforeEach(async () => {
    keycloakStub = {
      tokenParsed: undefined,
      token: 'fake-token',
      logout: jasmine.createSpy('logout'),
      updateToken: jasmine.createSpy('updateToken').and.resolveTo(false),
    };

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should have an undefined username when tokenParsed has no preferred_username', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.username).toBeUndefined();
  });

  it('should reflect the preferred_username from the parsed token', () => {
    keycloakStub.tokenParsed = { preferred_username: 'jane.doe' };
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.username).toBe('jane.doe');
  });

  it('should call keycloak.logout on logout()', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.componentInstance.logout();
    expect(keycloakStub.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });

  it('should clear the chat session id on logout', () => {
    localStorage.setItem('hermes-chat-session', 'some-old-session-id');
    const fixture = TestBed.createComponent(AppComponent);

    fixture.componentInstance.logout();

    expect(localStorage.getItem('hermes-chat-session')).toBeNull();
  });

  it('should report isAdmin false when tokenParsed has no admin realm role', () => {
    keycloakStub.tokenParsed = { realm_access: { roles: ['user'] } };
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.isAdmin).toBeFalse();
  });

  it('should report isAdmin true when tokenParsed has the admin realm role', () => {
    keycloakStub.tokenParsed = { realm_access: { roles: ['admin'] } };
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.isAdmin).toBeTrue();
  });
});
