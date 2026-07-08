import Keycloak from 'keycloak-js';

export function isAdminUser(keycloak: Keycloak): boolean {
  const roles = keycloak.tokenParsed?.['realm_access'] as { roles?: string[] } | undefined;
  return roles?.roles?.includes('admin') ?? false;
}
