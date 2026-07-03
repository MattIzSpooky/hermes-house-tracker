import { AuthGuardData, createAuthGuard } from 'keycloak-angular';
import { CanActivateFn } from '@angular/router';

const isAuthenticated = async (
  _route: unknown,
  _state: unknown,
  authData: AuthGuardData,
): Promise<boolean> => authData.authenticated;

export const authGuard: CanActivateFn = createAuthGuard(isAuthenticated);
