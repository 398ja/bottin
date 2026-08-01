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
  const defaults = [{ url: 'wss://seed', read: true, write: true }];

  function mockDefaults(ok, body) {
    global.fetch = vi.fn(() => Promise.resolve({ ok: ok, json: () => Promise.resolve(body) }));
  }

  // With no stored list, seeds from the defaults endpoint and saves.
  it('seeds from the defaults endpoint when absent', async () => {
    mockDefaults(true, { relays: defaults });
    const seeded = await APP.ensureRelaysSeeded(USER);
    expect(seeded).toEqual(defaults);
    expect(APP.loadRelays(USER)).toEqual(defaults);
    expect(global.fetch).toHaveBeenCalledWith('/api/v1/relays/defaults', expect.anything());
  });

  // With a usable write relay stored, does not fetch and keeps the existing list.
  it('is a no-op when a write relay already exists', async () => {
    const existing = [{ url: 'wss://mine', read: true, write: true }];
    APP.saveRelays(USER, existing);
    global.fetch = vi.fn();
    const result = await APP.ensureRelaysSeeded(USER);
    expect(result).toEqual(existing);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  // A read-only list cannot publish, so the defaults are added alongside it.
  it('adds the defaults when no stored relay is writable', async () => {
    const readOnly = [{ url: 'wss://mine', read: true, write: false }];
    APP.saveRelays(USER, readOnly);
    mockDefaults(true, { relays: defaults });
    const result = await APP.ensureRelaysSeeded(USER);
    expect(result).toEqual(readOnly.concat(defaults));
    expect(APP.loadRelays(USER)).toEqual(readOnly.concat(defaults));
  });

  // A failed defaults request must not be persisted as an empty relay list.
  it('leaves the stored list untouched when the request fails', async () => {
    mockDefaults(false, { status: 'error', code: 'NAP_SESSION_REQUIRED' });
    const result = await APP.ensureRelaysSeeded(USER);
    expect(result).toEqual([]);
    expect(localStorage.getItem(APP.relaysKey(USER))).toBeNull();
  });
});

describe('single identity per browser', () => {
  const OTHER = 'npub1other';

  // Everything resolves "the" identity through getIdentityUserId(), which returns
  // whichever record storage enumerates first. A second one would make the active
  // identity arbitrary, so signing in as someone else replaces rather than adds.
  it('evicts the previous identity and its data when another is saved', () => {
    APP.saveIdentity({ userId: OTHER, privateKeyEncrypted: 'old' });
    APP.saveFollowList(OTHER, ['npub1friend']);
    APP.saveBlockList(OTHER, ['npub1blocked']);
    APP.saveRelays(OTHER, [{ url: 'wss://old', read: true, write: true }]);
    APP.setSessionKey(OTHER, 'deadbeef');

    APP.saveIdentity({ userId: USER, privateKeyEncrypted: 'new' });

    expect(APP.storedIdentityUserIds()).toEqual([USER]);
    expect(APP.loadIdentity(OTHER)).toBeNull();
    expect(APP.loadFollowList(OTHER)).toEqual([]);
    expect(APP.loadBlockList(OTHER)).toEqual([]);
    expect(localStorage.getItem(APP.relaysKey(OTHER))).toBeNull();
    expect(APP.getSessionKey(OTHER)).toBeNull();
  });

  // Saving a profile edit must not wipe the editor's own follow list.
  it('keeps its own data when the same identity is saved again', () => {
    APP.saveIdentity({ userId: USER, displayName: 'First' });
    APP.saveFollowList(USER, ['npub1friend']);

    APP.saveIdentity({ userId: USER, displayName: 'Second' });

    expect(APP.loadIdentity(USER).displayName).toBe('Second');
    expect(APP.loadFollowList(USER)).toEqual(['npub1friend']);
  });
});

describe('logout', () => {
  function stubConfirm(answer) {
    vi.spyOn(window, 'confirm').mockReturnValue(answer);
    // A pending request keeps the redirect from firing, which jsdom cannot follow.
    global.fetch = vi.fn(() => new Promise(() => {}));
  }

  // Logout wipes the stored identity, so the unlock screen would have nothing to
  // unlock; the entry page is where signing in starts again.
  it('sends the user to the entry page, not the unlock screen', () => {
    expect(APP.logout.toString()).toContain("'/onboarding'");
    expect(APP.logout.toString()).not.toContain("'/login'");
  });

  // Logging out must leave no key material behind in this browser: neither the
  // encrypted identity, the unlocked session key, nor a half-finished onboarding nsec.
  it('erases the stored key material', () => {
    APP.saveIdentity({ userId: USER, privateKeyEncrypted: 'cipher' });
    APP.saveRelays(USER, [{ url: 'wss://a', read: true, write: true }]);
    APP.setSessionKey(USER, 'deadbeef');
    sessionStorage.setItem('onboarding-nsec', 'nsec1plaintext');
    stubConfirm(true);

    APP.logout();

    expect(localStorage.getItem(APP.identityKey(USER))).toBeNull();
    expect(localStorage.getItem(APP.relaysKey(USER))).toBeNull();
    expect(APP.getSessionKey(USER)).toBeNull();
    expect(sessionStorage.getItem('onboarding-nsec')).toBeNull();
  });

  // Declining the confirmation is not a logout, so the key must survive it.
  it('keeps the stored identity when the confirmation is declined', () => {
    APP.saveIdentity({ userId: USER, privateKeyEncrypted: 'cipher' });
    stubConfirm(false);

    APP.logout();

    expect(APP.loadIdentity(USER)).not.toBeNull();
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
