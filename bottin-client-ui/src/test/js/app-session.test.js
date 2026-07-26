import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/app.js';

const APP = window.APP;
const USER = 'npub1test';

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
});

describe('relay storage', () => {
  // Round-trips the relay list through localStorage; missing -> empty array.
  it('loads empty then persists and reloads', () => {
    expect(APP.loadRelays(USER)).toEqual([]);
    const relays = [{ url: 'wss://a', read: true, write: true }];
    APP.saveRelays(USER, relays);
    expect(APP.loadRelays(USER)).toEqual(relays);
  });
});

describe('ensureRelaysSeeded', () => {
  // With no stored list, seeds from the defaults endpoint and saves.
  it('seeds from the defaults endpoint when absent', async () => {
    const defaults = [{ url: 'wss://seed', read: true, write: true }];
    global.fetch = vi.fn(() => Promise.resolve({ json: () => Promise.resolve({ relays: defaults }) }));
    const seeded = await APP.ensureRelaysSeeded(USER);
    expect(seeded).toEqual(defaults);
    expect(APP.loadRelays(USER)).toEqual(defaults);
    expect(global.fetch).toHaveBeenCalledWith('/api/v1/relays/defaults', expect.anything());
  });

  // With a stored list, does not fetch and keeps the existing list.
  it('is a no-op when a list already exists', async () => {
    const existing = [{ url: 'wss://mine', read: true, write: false }];
    APP.saveRelays(USER, existing);
    global.fetch = vi.fn();
    const result = await APP.ensureRelaysSeeded(USER);
    expect(result).toEqual(existing);
    expect(global.fetch).not.toHaveBeenCalled();
  });
});

describe('session key', () => {
  // A stored key reads back until it expires, then getSessionKey returns null.
  it('stores, reads, expires and locks', () => {
    APP.setSessionKey(USER, 'deadbeef');
    expect(APP.getSessionKey(USER)).toBe('deadbeef');

    const raw = JSON.parse(sessionStorage.getItem(APP.sessionKey(USER)));
    raw.expiresAt = Date.now() - 1000;
    sessionStorage.setItem(APP.sessionKey(USER), JSON.stringify(raw));
    expect(APP.getSessionKey(USER)).toBeNull();

    APP.setSessionKey(USER, 'cafe');
    APP.lockSession(USER);
    expect(APP.getSessionKey(USER)).toBeNull();
  });
});
