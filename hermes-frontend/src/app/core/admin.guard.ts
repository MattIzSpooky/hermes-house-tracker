import { AuthGuardData, createAuthGuard } from 'keycloak-angular';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { inject } from '@angular/core';

const isAdminUser = async (
  _route: unknown,
  _state: unknown,
  authData: AuthGuardData,
): Promise<boolean | UrlTree> => {
  const { grantedRoles } = authData;

  if (grantedRoles.realmRoles.includes('admin')) {
    return true;
  }

  return inject(Router).parseUrl('/listings');
};

export const adminGuard: CanActivateFn = createAuthGuard(isAdminUser);
