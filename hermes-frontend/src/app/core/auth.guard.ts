import { AuthGuardData, createAuthGuard } from 'keycloak-angular';
import { CanActivateFn, RouterStateSnapshot } from '@angular/router';

const isAuthenticated = async (
  _route: unknown,
  state: RouterStateSnapshot,
  authData: AuthGuardData,
): Promise<boolean> => {
  const { authenticated, keycloak } = authData;

  if (!authenticated) {
    await keycloak.login({ redirectUri: window.location.origin + state.url });
    return false;
  }

  return true;
};

export const authGuard: CanActivateFn = createAuthGuard(isAuthenticated);
