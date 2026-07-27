import { describe, it, expect, beforeEach, vi } from 'vitest';
import Blossom from '../../main/resources/static/js/blossom.js';

// A stand-in for a browser File: jsdom's File has no arrayBuffer(), and those
// three members are all blossom.js ever reads.
function fakeFile(type, size, bytes) {
  const data = new Uint8Array(bytes || [1, 2, 3]);
  return { type: type, size: size, arrayBuffer: () => Promise.resolve(data) };
}

function signer(unsigned) {
  return Object.assign({}, unsigned, { id: 'abc', sig: 'def', pubkey: 'cafe' });
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('rejectionReason', () => {
  // A JPEG under the cap is acceptable, so no reason is returned.
  it('accepts an image within the size cap', () => {
    expect(Blossom.rejectionReason(fakeFile('image/jpeg', 1024))).toBeNull();
  });

  // A non-image MIME type is refused before any network call is made.
  it('rejects a non-image file', () => {
    expect(Blossom.rejectionReason(fakeFile('application/pdf', 1024)))
      .toBe('Choose an image file.');
  });

  // Anything over 5 MB is refused so the server never has to.
  it('rejects a file over the size cap', () => {
    expect(Blossom.rejectionReason(fakeFile('image/png', Blossom.MAX_BYTES + 1)))
      .toBe('Image must be 5 MB or smaller.');
  });
});

describe('sha256Hex', () => {
  // The digest is the lowercase hex SHA-256 of the file's bytes.
  it('hashes the file bytes', async () => {
    const hex = await Blossom.sha256Hex(fakeFile('image/png', 3, [1, 2, 3]));
    expect(hex).toBe('039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81');
  });
});

describe('buildAuthEvent', () => {
  // The BUD-01 auth event is kind 24242 with the upload verb, the file hash,
  // and an expiry in the future.
  it('builds a kind-24242 upload event carrying the hash and an expiry', () => {
    const now = Math.floor(Date.now() / 1000);
    const ev = Blossom.buildAuthEvent('deadbeef', 300);
    expect(ev.kind).toBe(24242);
    expect(ev.content).toBe('Upload image');
    expect(ev.tags).toContainEqual(['t', 'upload']);
    expect(ev.tags).toContainEqual(['x', 'deadbeef']);
    const expiration = Number(ev.tags.find((t) => t[0] === 'expiration')[1]);
    expect(expiration).toBeGreaterThanOrEqual(now + 300);
  });
});

describe('encodeAuthHeader', () => {
  // The header is the base64 of the signed event behind a `Nostr ` scheme.
  it('base64-encodes the signed event behind the Nostr scheme', () => {
    const signed = { kind: 24242, sig: 'ff' };
    const header = Blossom.encodeAuthHeader(signed);
    expect(header.startsWith('Nostr ')).toBe(true);
    expect(JSON.parse(atob(header.slice('Nostr '.length)))).toEqual(signed);
  });
});

describe('upload', () => {
  // A successful upload PUTs the raw file to {blossomUrl}/upload with the auth
  // header and resolves with the blob descriptor the server returned.
  it('PUTs the file and resolves with the blob descriptor', async () => {
    const descriptor = { url: 'http://blossom.test/abc.png', sha256: 'abc', size: 3, type: 'image/png' };
    global.fetch = vi.fn(() => Promise.resolve({
      ok: true,
      status: 201,
      headers: { get: () => null },
      json: () => Promise.resolve(descriptor),
    }));

    const file = fakeFile('image/png', 3);
    const result = await Blossom.upload(file, 'http://blossom.test', signer);

    expect(result).toEqual(descriptor);
    const [url, init] = global.fetch.mock.calls[0];
    expect(url).toBe('http://blossom.test/upload');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe(file);
    expect(init.headers.Authorization.startsWith('Nostr ')).toBe(true);
  });

  // A trailing slash on the configured URL must not produce a double slash.
  it('normalises a trailing slash on the blossom URL', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 201, headers: { get: () => null }, json: () => Promise.resolve({}),
    }));
    await Blossom.upload(fakeFile('image/png', 3), 'http://blossom.test/', signer);
    expect(global.fetch.mock.calls[0][0]).toBe('http://blossom.test/upload');
  });

  // A rejection is surfaced with the server's X-Reason text so the user learns why.
  it('rejects with the X-Reason header on a non-2xx response', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: false,
      status: 401,
      headers: { get: (name) => (name === 'X-Reason' ? 'Missing auth event' : null) },
      json: () => Promise.resolve({}),
    }));
    await expect(Blossom.upload(fakeFile('image/png', 3), 'http://blossom.test', signer))
      .rejects.toThrow('Upload rejected: Missing auth event');
  });

  // Without an X-Reason header the status code stands in as the detail.
  it('falls back to the status code when X-Reason is absent', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: false, status: 413, headers: { get: () => null }, json: () => Promise.resolve({}),
    }));
    await expect(Blossom.upload(fakeFile('image/png', 3), 'http://blossom.test', signer))
      .rejects.toThrow('Upload rejected: HTTP 413');
  });

  // An unusable file is refused locally, so no request reaches the server.
  it('rejects an oversized file without calling fetch', async () => {
    global.fetch = vi.fn();
    await expect(Blossom.upload(fakeFile('image/png', Blossom.MAX_BYTES + 1), 'http://blossom.test', signer))
      .rejects.toThrow('Image must be 5 MB or smaller.');
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
