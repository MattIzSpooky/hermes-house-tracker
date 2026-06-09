import { EuroPricePipe } from './euro-price.pipe';

describe('EuroPricePipe', () => {
  const pipe = new EuroPricePipe();

  it('formats a whole number in Dutch locale', () => {
    expect(pipe.transform(450000)).toBe('€ 450.000');
  });

  it('returns dash for null', () => {
    expect(pipe.transform(null)).toBe('—');
  });

  it('returns dash for undefined', () => {
    expect(pipe.transform(undefined)).toBe('—');
  });

  it('formats small values', () => {
    expect(pipe.transform(0)).toBe('€ 0');
  });
});
