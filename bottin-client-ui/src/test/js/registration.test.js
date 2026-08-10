import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/app.js';
import '../../main/resources/static/js/nostr-publish.js';
import '../../main/resources/static/js/registration.js';

const APP = window.APP;

const DOMAIN = 'imani.test';
const NSEC = 'nsec1generated';
const NPUB = 'npub1generated';
const PRIVATE_HEX = 'a'.repeat(64);
const PUBLIC_HEX = 'b'.repeat(64);
const SYSTEM_RELAYS = ['wss://relay.one'];

// The real APP.saveIdentity is left in place so that "nothing was stored" is
// asserted against localStorage itself rather than against a mock that was
// merely not called. Only the two collaborators that would reach the network
// are replaced.
const realSaveIdentity = APP.saveIdentity.bind(APP);

// The sequence of side effects, in the order they actually happened. The whole
// feature is an ordering guarantee, so the order is what the tests read.
let order;

function storedIdentityKeys() {
  return Object.keys(localStorage).filter((key) => key.startsWith('imani.identity.'));
}

function respondToRegister(response) {
  global.fetch = vi.fn(() => {
    order.push('register');
    return Promise.resolve(response());
  });
}

function registered() {
  return { ok: true, status: 200, json: () => Promise.resolve({ status: 'registered', nip05: 'alice@' + DOMAIN }) };
}

function handleTaken() {
  return { ok: false, status: 409, json: () => Promise.resolve({ status: 'error', code: 'USERNAME_TAKEN' }) };
}

function submit(overrides) {
  return window.Registration.submit(Object.assign(
    { username: 'alice', password: 'correct horse', domain: DOMAIN }, overrides));
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
  window.Registration.reset();
  order = [];

  window.NostrCrypto = {
    generateKeypair: vi.fn(() => {
      order.push('generate');
      return { nsec: NSEC, npub: NPUB, privateKeyHex: PRIVATE_HEX, publicKeyHex: PUBLIC_HEX };
    }),
    buildEncryptedIdentity: vi.fn(() => Promise.resolve({
      userId: NPUB, npub: NPUB, pubkeyHex: PUBLIC_HEX, privateKeyEncrypted: 'x'
    })),
    signEvent: (unsigned) => ({ ...unsigned, id: 'signed', sig: 'sig' })
  };
  window.NostrTools = { SimplePool: function () {} };

  vi.spyOn(APP, 'napLogin').mockImplementation(() => {
    order.push('login');
    return Promise.resolve();
  });
  vi.spyOn(APP, 'systemRelays').mockResolvedValue(SYSTEM_RELAYS);
  vi.spyOn(APP, 'saveIdentity').mockImplementation((identity) => {
    order.push('save');
    return realSaveIdentity(identity);
  });
  vi.spyOn(window.NostrPublish, 'publish').mockImplementation(() => {
    order.push('publish');
    return Promise.resolve([{ url: SYSTEM_RELAYS[0], accepted: true, reason: null }]);
  });

  respondToRegister(registered);
});

describe('Registration.submit ordering', () => {
  // The feature's central guarantee: the directory is asked for the handle
  // before anything about this identity is written to the browser.
  it('claims the handle before storing the identity', async () => {
    await submit();

    expect(order.indexOf('register')).toBeGreaterThan(-1);
    expect(order.indexOf('save')).toBeGreaterThan(order.indexOf('register'));
  });

  // The key must exist to authenticate the claim, so sign-in precedes it; but
  // existing is not the same as being persisted.
  it('signs in with the generated key before claiming', async () => {
    await submit();

    expect(order).toEqual(['generate', 'login', 'register', 'save', 'publish']);
    expect(APP.napLogin).toHaveBeenCalledWith(PRIVATE_HEX, NPUB);
  });
});

describe('Registration.submit when the handle is taken', () => {
  // A lost race must leave no account behind. Before this feature the identity
  // was already written by this point, carrying a nip05 the directory had just
  // given to somebody else.
  it('stores no identity in the browser', async () => {
    respondToRegister(handleTaken);

    await expect(submit()).rejects.toThrow();

    expect(storedIdentityKeys()).toEqual([]);
  });

  // The caller distinguishes a taken handle, which returns the user to the
  // form, from every other failure.
  it('rejects with the USERNAME_TAKEN code', async () => {
    respondToRegister(handleTaken);

    await expect(submit()).rejects.toMatchObject({ code: 'USERNAME_TAKEN' });
  });

  // Retrying with a free handle must not cost the user their password or mint a
  // second key: the NAP session already established belongs to the same key.
  it('reuses the same key and session on a retry', async () => {
    respondToRegister(handleTaken);
    await expect(submit()).rejects.toThrow();

    respondToRegister(registered);
    await submit({ username: 'bob' });

    expect(window.NostrCrypto.generateKeypair).toHaveBeenCalledTimes(1);
    expect(APP.napLogin).toHaveBeenCalledTimes(1);
    expect(storedIdentityKeys()).toEqual(['imani.identity.' + NPUB]);
  });
});

describe('Registration.submit when a step before the claim fails', () => {
  // Sign-in failing means no session, so no claim can be made and nothing may
  // be stored on the strength of a handle nobody granted.
  it('stores nothing when sign-in fails', async () => {
    APP.napLogin.mockRejectedValue(new Error('handshake refused'));

    await expect(submit()).rejects.toThrow(/handshake refused/);

    expect(global.fetch).not.toHaveBeenCalled();
    expect(storedIdentityKeys()).toEqual([]);
  });
});

describe('Registration.submit publishing', () => {
  // A relay that will not take the profile does not undo a claimed handle: the
  // account exists, and the failure is reported rather than thrown.
  it('resolves with a usable account when publishing fails', async () => {
    window.NostrPublish.publish.mockRejectedValue(new Error('all relays refused'));

    const outcome = await submit();

    expect(outcome.publishError).toMatch(/all relays refused/);
    expect(storedIdentityKeys()).toEqual(['imani.identity.' + NPUB]);
  });

  // With no relay configured there is nothing to publish to, and saying so is
  // more useful than reporting a successful publish to zero relays.
  it('reports a missing relay instead of publishing', async () => {
    APP.systemRelays.mockResolvedValue([]);

    const outcome = await submit();

    expect(window.NostrPublish.publish).not.toHaveBeenCalled();
    expect(outcome.publishError).toBe('no write relay configured');
    expect(outcome.relayCount).toBe(0);
  });

  // The published profile is signed by the key the handle was claimed for.
  it('publishes a kind-0 event to the system relays', async () => {
    await submit();

    expect(window.NostrPublish.publish).toHaveBeenCalledWith(
      expect.anything(), SYSTEM_RELAYS, expect.objectContaining({ kind: 0 }));
  });
});

describe('Registration.submit directory call', () => {
  // The pubkey is deliberately absent: the server reads it from the NAP session,
  // so a handle can only be claimed for the key that signed in.
  it('sends the handle and the relays, and no key', async () => {
    await submit();

    expect(global.fetch).toHaveBeenCalledWith('/api/v1/register', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(global.fetch.mock.calls[0][1].body))
      .toEqual({ username: 'alice', relays: SYSTEM_RELAYS });
  });

  // The stored identity carries the handle it was granted, and nothing else the
  // registration form no longer collects.
  it('stores the identity carrying only its nip05', async () => {
    await submit();

    const stored = JSON.parse(localStorage.getItem('imani.identity.' + NPUB));
    expect(stored.nip05).toBe('alice@' + DOMAIN);
    expect(stored.displayName).toBeUndefined();
    expect(stored.picture).toBeUndefined();
    expect(stored.about).toBeUndefined();
  });

  // The backup screen needs the key that was just minted, and it is the only
  // thing handed across the page boundary.
  it('returns the nsec for the backup screen', async () => {
    const outcome = await submit();

    expect(outcome.nsec).toBe(NSEC);
  });
});
