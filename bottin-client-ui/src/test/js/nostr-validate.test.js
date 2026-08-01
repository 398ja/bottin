import { describe, it, expect } from 'vitest';
import NostrValidate from '../../main/resources/static/js/nostr-validate.js';

describe('isSafeHttpUrl', () => {
  it('accepts empty and http(s) URLs, rejects other schemes', () => {
    expect(NostrValidate.isSafeHttpUrl('')).toBe(true);
    expect(NostrValidate.isSafeHttpUrl('https://x/y.png')).toBe(true);
    expect(NostrValidate.isSafeHttpUrl('http://x')).toBe(true);
    expect(NostrValidate.isSafeHttpUrl('javascript:alert(1)')).toBe(false);
    expect(NostrValidate.isSafeHttpUrl('not a url')).toBe(false);
  });
});

describe('isValidLud16', () => {
  it('accepts empty and user@domain, rejects malformed', () => {
    expect(NostrValidate.isValidLud16('')).toBe(true);
    expect(NostrValidate.isValidLud16('alice@walletofsatoshi.com')).toBe(true);
    expect(NostrValidate.isValidLud16('alice')).toBe(false);
    expect(NostrValidate.isValidLud16('alice@nodot')).toBe(false);
  });
});

describe('isValidDisplayName', () => {
  it('accepts empty and bounded length, rejects over 128 chars', () => {
    expect(NostrValidate.isValidDisplayName('')).toBe(true);
    expect(NostrValidate.isValidDisplayName('Alice')).toBe(true);
    expect(NostrValidate.isValidDisplayName('a'.repeat(129))).toBe(false);
  });
});

describe('validateProfileFields', () => {
  it('collects errors for each invalid field', () => {
    const result = NostrValidate.validateProfileFields({
      display_name: 'a'.repeat(200), picture: 'javascript:x',
      banner: 'https://ok', website: '', lud16: 'bad',
    });
    expect(result.valid).toBe(false);
    expect(Object.keys(result.errors).sort()).toEqual(['display_name', 'lud16', 'picture']);
  });

  it('passes clean input', () => {
    const result = NostrValidate.validateProfileFields({
      display_name: 'Alice', picture: 'https://x/a.png',
      banner: '', website: 'https://alice.example', lud16: 'alice@ln.example',
    });
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual({});
  });
});
