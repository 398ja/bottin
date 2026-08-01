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

describe('system relays and effective relay lists', () => {
  const SYSTEM = ['ws://relay-a:7777', 'wss://relay-b.example'];

  function mockSystemRelays(ok, body) {
    global.fetch = vi.fn(() => Promise.resolve({ ok: ok, json: () => Promise.resolve(body) }));
  }

  // The endpoint belongs to the client server and replaces /defaults, which
  // described the opposite of what now happens.
  it('reads the system relays from the client server', async () => {
    mockSystemRelays(true, { relays: SYSTEM });
    await expect(APP.systemRelays()).resolves.toEqual(SYSTEM);
    expect(global.fetch).toHaveBeenCalledWith('/api/v1/relays/system', expect.anything());
  });

  // A brand-new identity owns no relays yet still publishes, because the system
  // relays are applied rather than copied in.
  it('unions the system relays in when the user has none', async () => {
    mockSystemRelays(true, { relays: SYSTEM });
    await expect(APP.effectiveWriteRelays(USER)).resolves.toEqual(SYSTEM);
  });

  // The user's own relays lead, and a system relay they already listed is not repeated.
  it('puts the user relays first and de-duplicates by url', async () => {
    APP.saveRelays(USER, [
      { url: 'wss://mine', read: true, write: true },
      { url: 'ws://relay-a:7777', read: true, write: true }
    ]);
    mockSystemRelays(true, { relays: SYSTEM });
    await expect(APP.effectiveWriteRelays(USER)).resolves
      .toEqual(['wss://mine', 'ws://relay-a:7777', 'wss://relay-b.example']);
  });

  // A relay the user marked read-only must not receive publishes.
  it('excludes the user read-only relays from the write list', async () => {
    APP.saveRelays(USER, [{ url: 'wss://readonly', read: true, write: false }]);
    mockSystemRelays(true, { relays: [] });
    await expect(APP.effectiveWriteRelays(USER)).resolves.toEqual([]);
  });

  // The read path unions the same way, or a new user could never read back the
  // profile they just published to the system relays.
  it('unions the system relays into the read list', async () => {
    APP.saveRelays(USER, [{ url: 'wss://readonly', read: true, write: false }]);
    mockSystemRelays(true, { relays: SYSTEM });
    await expect(APP.effectiveReadRelays(USER)).resolves
      .toEqual(['wss://readonly'].concat(SYSTEM));
  });

  // An unreachable endpoint must not stop a user publishing to relays they own.
  it('falls back to the user relays when the endpoint fails', async () => {
    APP.saveRelays(USER, [{ url: 'wss://mine', read: true, write: true }]);
    mockSystemRelays(false, {});
    await expect(APP.effectiveWriteRelays(USER)).resolves.toEqual(['wss://mine']);
  });

  // Nothing is copied into the browser. Seeding is what froze the relay set per
  // browser and put system relays into the user's own editable list.
  it('never writes the system relays into stored state', async () => {
    mockSystemRelays(true, { relays: SYSTEM });
    await APP.effectiveWriteRelays(USER);
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
