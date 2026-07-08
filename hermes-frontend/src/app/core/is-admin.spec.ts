import Keycloak from 'keycloak-js';
import { isAdminUser } from './is-admin';

describe('isAdminUser', () => {
  it('returns false when tokenParsed is undefined', () => {
    const keycloak = { tokenParsed: undefined } as unknown as Keycloak;
    expect(isAdminUser(keycloak)).toBeFalse();
  });

  it('returns false when tokenParsed has no admin realm role', () => {
    const keycloak = { tokenParsed: { realm_access: { roles: ['user'] } } } as unknown as Keycloak;
    expect(isAdminUser(keycloak)).toBeFalse();
  });

  it('returns true when tokenParsed has the admin realm role', () => {
    const keycloak = { tokenParsed: { realm_access: { roles: ['admin'] } } } as unknown as Keycloak;
    expect(isAdminUser(keycloak)).toBeTrue();
  });
});
