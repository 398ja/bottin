import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/admin-signin.js';

const AdminSignIn = window.AdminSignIn;

const NSEC = 'nsec1adminkeyfortests';
const PASSPHRASE = 'correct horse battery staple';
const NPUB = 'npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6';
const HEX = '3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d';

/** What the shared crypto module would produce, without doing real key work. */
function encryptedIdentity() {
  return {
    userId: NPUB,
    npub: NPUB,
    pubkeyHex: HEX,
    privateKeyEncrypted: 'ENCRYPTED',
    privateKeyIv: 'IV',
    privateKeySalt: 'SALT',
    passwordHash: 'HASH',
    passwordSalt: 'PWSALT',
    createdAt: 1
  };
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();

  window.NostrCrypto = {
    buildEncryptedIdentity: vi.fn(() => Promise.resolve(encryptedIdentity())),
    nsecToHex: vi.fn(() => HEX)
  };
  window.NapClient = {
    login: vi.fn(() => Promise.resolve())
  };
});

describe('first sign-in', () => {
  // The key is kept so the administrator is not asked for it every session,
  // which is the habit that gets an nsec pasted somewhere it should not be.
  it('stores the encrypted identity on this device', async () => {
    await AdminSignIn.firstSignIn(NSEC, PASSPHRASE);

    const stored = AdminSignIn.stored();
    expect(stored).not.toBeNull();
    expect(stored.privateKeyEncrypted).toBe('ENCRYPTED');
    expect(stored.npub).toBe(NPUB);
  });

  // The whole security argument: the deployment learns the administrator holds
  // the key without ever receiving it.
  it('never stores the plaintext key or the passphrase', async () => {
    await AdminSignIn.firstSignIn(NSEC, PASSPHRASE);

    const everything = JSON.stringify(localStorage) + JSON.stringify(sessionStorage);
    expect(everything).not.toContain(NSEC);
    expect(everything).not.toContain(PASSPHRASE);
  });

  // Signing proves control of the key; the key itself is the input to signing,
  // not to the request.
  it('proves control of the key it just stored', async () => {
    await AdminSignIn.firstSignIn(NSEC, PASSPHRASE);

    expect(window.NapClient.login).toHaveBeenCalledWith(HEX, NPUB);
  });

  // A refused key left encrypted on the device would show an unlock prompt on
  // the next visit that is guaranteed to fail.
  it('leaves nothing stored when the key is refused', async () => {
    window.NapClient.login = vi.fn(() => Promise.reject(new Error('Authentication failed')));

    await expect(AdminSignIn.firstSignIn(NSEC, PASSPHRASE)).rejects.toThrow();

    expect(AdminSignIn.stored()).toBeNull();
    expect(localStorage.length).toBe(0);
  });

  // The caller has to be able to tell the administrator what went wrong.
  it('reports the refusal rather than swallowing it', async () => {
    window.NapClient.login = vi.fn(() => Promise.reject(new Error('Authentication failed')));

    await expect(AdminSignIn.firstSignIn(NSEC, PASSPHRASE))
      .rejects.toThrow('Authentication failed');
  });
});

describe('stored identity', () => {
  // Drives which form the sign-in page shows: the key and a new passphrase, or
  // the passphrase alone.
  it('reports nothing stored on a fresh device', () => {
    expect(AdminSignIn.stored()).toBeNull();
  });

  // The way back from a forgotten passphrase, which cannot be recovered.
  it('can be discarded deliberately', async () => {
    await AdminSignIn.firstSignIn(NSEC, PASSPHRASE);
    expect(AdminSignIn.stored()).not.toBeNull();

    AdminSignIn.forget();

    expect(AdminSignIn.stored()).toBeNull();
  });
});
