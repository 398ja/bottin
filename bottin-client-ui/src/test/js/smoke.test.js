import { describe, it, expect } from 'vitest';

// Proves Vitest + jsdom harness runs under `mvn verify`.
describe('js test harness', () => {
  it('runs and has DOM', () => {
    expect(typeof document).toBe('object');
    expect(1 + 1).toBe(2);
  });
});
