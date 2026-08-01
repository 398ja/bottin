import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/profile-fetch.js';

const PUBKEY = 'a'.repeat(64);
const USER = 'npub1test';

function event(content, createdAt) {
  return { kind: 0, pubkey: PUBKEY, created_at: createdAt, content: JSON.stringify(content) };
}

function poolReturning(events) {
  return { querySync: vi.fn(() => Promise.resolve(events)), close: vi.fn() };
}

describe('ProfileFetch.fetch', () => {
  // The published profile is what a fresh browser has to render from.
  it('maps published metadata onto identity fields', async () => {
    const pool = poolReturning([event({
      display_name: 'Alice', about: 'hi', picture: 'p.png', banner: 'b.png',
      nip05: 'alice@imani.test', lud16: 'alice@getalby.com', website: 'https://alice.example'
    }, 100)]);

    const profile = await window.ProfileFetch.fetch(pool, ['wss://one'], PUBKEY);

    expect(profile).toEqual({
      displayName: 'Alice', about: 'hi', picture: 'p.png', banner: 'b.png',
      nip05: 'alice@imani.test', lud16: 'alice@getalby.com', website: 'https://alice.example'
    });
  });

  // Relays retain different history, so the newest event wins rather than the
  // first one returned.
  it('takes the newest event when relays disagree', async () => {
    const pool = poolReturning([
      event({ display_name: 'Stale' }, 100),
      event({ display_name: 'Current' }, 500),
      event({ display_name: 'Older' }, 300)
    ]);

    const profile = await window.ProfileFetch.fetch(pool, ['wss://one', 'wss://two'], PUBKEY);

    expect(profile.displayName).toBe('Current');
  });

  // NIP-01 name is the fallback when no display_name was published.
  it('falls back to name when display_name is absent', async () => {
    const pool = poolReturning([event({ name: 'alice' }, 100)]);

    const profile = await window.ProfileFetch.fetch(pool, ['wss://one'], PUBKEY);

    expect(profile.displayName).toBe('alice');
  });

  // A sign-in must not hinge on a relay being reachable.
  it('returns null when the query fails or nothing is published', async () => {
    const failing = { querySync: vi.fn(() => Promise.reject(new Error('relay down'))), close: vi.fn() };
    const empty = poolReturning([]);

    expect(await window.ProfileFetch.fetch(failing, ['wss://one'], PUBKEY)).toBeNull();
    expect(await window.ProfileFetch.fetch(empty, ['wss://one'], PUBKEY)).toBeNull();
    expect(await window.ProfileFetch.fetch(empty, [], PUBKEY)).toBeNull();
  });
});

describe('ProfileFetch.applyTo', () => {
  // Silence from a relay is not an instruction to erase what is held locally.
  it('keeps local values where the published profile says nothing', () => {
    const identity = { displayName: 'Local', about: 'local bio', picture: 'local.png' };

    const merged = window.ProfileFetch.applyTo(identity, {
      displayName: 'Published', about: null, picture: '', banner: 'new-banner.png'
    });

    expect(merged.displayName).toBe('Published');
    expect(merged.about).toBe('local bio');
    expect(merged.picture).toBe('local.png');
    expect(merged.banner).toBe('new-banner.png');
  });
});

describe('ProfileFetch.refresh', () => {
  let saved;

  beforeEach(() => {
    saved = null;
    window.APP = {
      loadIdentity: () => ({ userId: USER, npub: USER, pubkeyHex: PUBKEY, displayName: 'Stored' }),
      saveIdentity: (identity) => { saved = identity; },
      effectiveReadRelays: vi.fn(() => Promise.resolve(['wss://read']))
    };
    window.NostrTools = { SimplePool: function () { return poolReturning([event({ display_name: 'Published' }, 100)]); } };
  });

  // Signing in on a browser holding a bare identity must end with the published
  // profile stored, which is what every page renders from.
  it('stores the published profile against the identity', async () => {
    const identity = await window.ProfileFetch.refresh(USER);

    expect(identity.displayName).toBe('Published');
    expect(saved.displayName).toBe('Published');
  });

  // Write-only relays hold nothing to read back.
  it('queries read relays only', async () => {
    let queriedRelays;
    window.NostrTools = { SimplePool: function () {
      return { querySync: (relays) => { queriedRelays = relays; return Promise.resolve([]); }, close: vi.fn() };
    } };

    await window.ProfileFetch.refresh(USER);

    expect(queriedRelays).toEqual(['wss://read']);
  });

  // An unreachable relay leaves the sign-in usable with what is stored.
  it('resolves with the stored identity when the lookup fails', async () => {
    window.APP.effectiveReadRelays = vi.fn(() => Promise.reject(new Error('offline')));

    const identity = await window.ProfileFetch.refresh(USER);

    expect(identity.displayName).toBe('Stored');
    expect(saved).toBeNull();
  });
});
