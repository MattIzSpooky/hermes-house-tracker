import { API_URL_PATTERN } from '../app.config';

describe('API_URL_PATTERN', () => {
  it('matches /api and its sub-paths', () => {
    expect(API_URL_PATTERN.test('/api')).toBeTrue();
    expect(API_URL_PATTERN.test('/api/listings')).toBeTrue();
    expect(API_URL_PATTERN.test('/api/favorites/abc-123/xyz')).toBeTrue();
  });

  it('does not match unrelated or non-/api paths', () => {
    expect(API_URL_PATTERN.test('/api-something-else')).toBeFalse();
    expect(API_URL_PATTERN.test('/listings')).toBeFalse();
    expect(API_URL_PATTERN.test('http://localhost:8081/realms/hermes')).toBeFalse();
  });
});
