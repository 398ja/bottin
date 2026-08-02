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

describe('unlocking a stored key', () => {
  beforeEach(() => {
    window.NostrCrypto.verifyPassword = vi.fn(() => Promise.resolve(true));
    window.NostrCrypto.decryptPrivateKey = vi.fn(() => Promise.resolve(HEX));
  });

  async function withStoredIdentity() {
    await AdminSignIn.firstSignIn(NSEC, PASSPHRASE);
    window.NapClient.login = vi.fn(() => Promise.resolve());
  }

  // The everyday path: the key is already here, so only the passphrase is asked
  // for. A raw nsec is handled once per device rather than once per session.
  it('decrypts with the right passphrase and proves control', async () => {
    await withStoredIdentity();

    await AdminSignIn.unlock(PASSPHRASE);

    expect(window.NostrCrypto.decryptPrivateKey).toHaveBeenCalled();
    expect(window.NapClient.login).toHaveBeenCalledWith(HEX, NPUB);
  });

  // A wrong passphrase must not cost the administrator their stored key.
  it('rejects a wrong passphrase and leaves the stored key untouched', async () => {
    await withStoredIdentity();
    const before = localStorage.getItem(AdminSignIn.STORAGE_KEY);
    window.NostrCrypto.verifyPassword = vi.fn(() => Promise.resolve(false));

    await expect(AdminSignIn.unlock('wrong')).rejects.toThrow();

    expect(localStorage.getItem(AdminSignIn.STORAGE_KEY)).toBe(before);
  });

  // Verified before decryption is attempted, so a wrong passphrase fails fast
  // and cannot be distinguished from a corrupt key by timing alone.
  it('does not attempt decryption when the passphrase is wrong', async () => {
    await withStoredIdentity();
    window.NostrCrypto.verifyPassword = vi.fn(() => Promise.resolve(false));

    await expect(AdminSignIn.unlock('wrong')).rejects.toThrow();

    expect(window.NostrCrypto.decryptPrivateKey).not.toHaveBeenCalled();
  });

  // One wrong attempt must not lock the administrator out of their own device.
  it('allows another attempt after a wrong passphrase', async () => {
    await withStoredIdentity();
    window.NostrCrypto.verifyPassword = vi.fn(() => Promise.resolve(false));
    await expect(AdminSignIn.unlock('wrong')).rejects.toThrow();

    window.NostrCrypto.verifyPassword = vi.fn(() => Promise.resolve(true));
    await AdminSignIn.unlock(PASSPHRASE);

    expect(window.NapClient.login).toHaveBeenCalled();
  });

  // Nothing to unlock is a different situation from a wrong passphrase, and the
  // page needs to tell them apart to choose which form to show.
  it('reports clearly when this device holds no key', async () => {
    await expect(AdminSignIn.unlock(PASSPHRASE)).rejects.toThrow(/no stored key/i);
  });

  // The contrast that the whole design rests on. firstSignIn discards a refused
  // key; unlock must not, or an unreachable server would cost the administrator
  // their stored key and force them to find their nsec again.
  it('keeps the stored key when the handshake fails', async () => {
    await withStoredIdentity();
    window.NapClient.login = vi.fn(() => Promise.reject(new Error('Authentication failed')));

    await expect(AdminSignIn.unlock(PASSPHRASE)).rejects.toThrow();

    expect(AdminSignIn.stored()).not.toBeNull();
  });
});

describe('signing out', () => {
  beforeEach(() => {
    window.NapClient.logout = vi.fn(() => Promise.resolve());
  });

  async function signedIn() {
    await AdminSignIn.firstSignIn(NSEC, PASSPHRASE);
  }

  // Ending the session and removing the key are one action. Either half alone
  // is a bug: a session left alive keeps a cookie the browser presents, and a
  // key left behind means "signed out" was a false reassurance.
  it('ends the session and erases the key', async () => {
    await signedIn();

    await AdminSignIn.signOut();

    expect(window.NapClient.logout).toHaveBeenCalled();
    expect(AdminSignIn.stored()).toBeNull();
    expect(localStorage.length).toBe(0);
  });

  // A key left on a device is the worse outcome, and the session expires on its
  // own — so the erase is not conditional on the server answering.
  it('erases the key even when the logout request fails', async () => {
    await signedIn();
    window.NapClient.logout = vi.fn(() => Promise.reject(new Error('offline')));

    await expect(AdminSignIn.signOut()).rejects.toThrow();

    expect(AdminSignIn.stored()).toBeNull();
  });

  // The administrator has to be able to learn that the server side did not
  // complete, even though the local half did.
  it('reports a failed logout rather than swallowing it', async () => {
    await signedIn();
    window.NapClient.logout = vi.fn(() => Promise.reject(new Error('offline')));

    await expect(AdminSignIn.signOut()).rejects.toThrow('offline');
  });

  // Signing out on a device with nothing stored must not fail; the session may
  // still exist even when the key has already been discarded.
  it('still ends the session when nothing is stored locally', async () => {
    await AdminSignIn.signOut();

    expect(window.NapClient.logout).toHaveBeenCalled();
  });
});
