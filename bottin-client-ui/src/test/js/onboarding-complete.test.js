import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/nostr-publish.js';
import '../../main/resources/static/js/onboarding-complete.js';

const NSEC = 'nsec1test';
const HEX_KEY = 'b'.repeat(64);
const USER = 'npub1test';

const identity = {
  userId: USER,
  npub: USER,
  nip05: 'alice@imani.test',
  displayName: 'Alice',
  about: 'hello'
};

let relays;

beforeEach(() => {
  relays = [
    { url: 'wss://write.one', read: true, write: true },
    { url: 'wss://read.only', read: true, write: false }
  ];

  window.APP = {
    getIdentityUserId: () => USER,
    loadIdentity: () => identity,
    napLogin: vi.fn(() => Promise.resolve()),
    ensureRelaysSeeded: vi.fn(() => Promise.resolve(relays))
  };
  window.NostrCrypto = {
    nsecToHex: () => HEX_KEY,
    signEvent: (unsigned) => ({ ...unsigned, id: 'signed', sig: 'sig' })
  };
  window.NostrTools = { SimplePool: function () {} };

  vi.spyOn(window.NostrPublish, 'publish')
    .mockResolvedValue([{ url: 'wss://write.one', accepted: true, reason: null }]);

  global.fetch = vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }));
});

describe('OnboardingComplete.run', () => {
  // The whole point of the step: the handle reaches the directory and the profile
  // reaches the relays, using the key that was just minted.
  it('registers the handle and publishes the profile to write relays', async () => {
    const summary = await window.OnboardingComplete.run(NSEC);

    expect(window.APP.napLogin).toHaveBeenCalledWith(HEX_KEY, USER);
    expect(global.fetch).toHaveBeenCalledWith('/api/v1/register', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(global.fetch.mock.calls[0][1].body))
      .toEqual({ username: 'alice', relays: ['wss://write.one'] });
    expect(window.NostrPublish.publish).toHaveBeenCalledWith(
      expect.anything(), ['wss://write.one'], expect.objectContaining({ kind: 0 }));
    expect(summary).toMatchObject({ registered: true, published: 1, relayCount: 1, registerError: null });
  });

  // A handle someone else already holds must not cost the user their profile
  // publish: the two steps report independently.
  it('still publishes when the handle is taken', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: false, status: 409, json: () => Promise.resolve({ code: 'USERNAME_TAKEN' })
    }));

    const summary = await window.OnboardingComplete.run(NSEC);

    expect(summary.registered).toBe(false);
    expect(summary.registerError).toBe('USERNAME_TAKEN');
    expect(summary.published).toBe(1);
  });

  // With no write relay there is nothing to publish to, and saying so is more
  // useful than reporting a successful publish to zero relays.
  it('reports a missing write relay instead of publishing', async () => {
    relays = [{ url: 'wss://read.only', read: true, write: false }];

    const summary = await window.OnboardingComplete.run(NSEC);

    expect(window.NostrPublish.publish).not.toHaveBeenCalled();
    expect(summary.publishError).toBe('no write relay configured');
    expect(summary.relayCount).toBe(0);
  });

  // Without a stored identity there is no handle and no key to sign with, so the
  // step fails loudly rather than half-completing.
  it('rejects when no identity is stored', async () => {
    window.APP.getIdentityUserId = () => null;

    await expect(window.OnboardingComplete.run(NSEC)).rejects.toThrow(/No stored identity/);
  });
});
