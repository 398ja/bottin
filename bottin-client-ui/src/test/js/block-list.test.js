import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { TextEncoder as NodeTextEncoder } from 'node:util';

// Under vitest's jsdom, TextEncoder produces a Uint8Array from a different realm than
// the `Uint8Array` this module's `instanceof` checks against - measurably so:
// `new TextEncoder().encode('x') instanceof Uint8Array` is false. nip44.encrypt then
// dies with "Uint8Array expected" inside pad(), which unlike the bundle's utf8ToBytes
// does not re-wrap what the encoder returned.
//
// A browser has one realm and no such split, so this shim restores the browser's
// behaviour rather than papering over a product bug. Re-wrapping with Uint8Array.from
// lands the bytes in the realm the bundle will check them against.
class RealmSafeTextEncoder {
  encode(str) {
    return Uint8Array.from(new NodeTextEncoder().encode(str));
  }
}
globalThis.TextEncoder = RealmSafeTextEncoder;

// The real nostr-tools bundle, so the sealing is exercised rather than imitated - a
// fake nip44 would prove only that the fake round-trips.
//
// Read and evaluated rather than imported: the bundle lives in bottin-web-assets, and
// importing across that boundary lifts Vite's root above this module, which drops the
// jsdom environment and leaves `window` undefined. Evaluating it here keeps the real
// code and the browser-shaped globals.
// Located by probing rather than by a fixed relative path: vitest runs from the module
// under `mvn test` but from the repository root when invoked there, and import.meta.url
// is not a file: URL under Vite's transform. Failing loudly beats silently falling back
// to a fake, which would leave the sealing untested while the suite stayed green.
function nostrToolsBundle() {
  var relative = 'bottin-web-assets/src/main/resources/META-INF/resources/js/nostr-tools.js';
  var found = ['..', '.'].map((base) => path.resolve(base, relative)).find(existsSync);
  if (!found) throw new Error('nostr-tools bundle not found from ' + process.cwd());
  return found;
}

const nt = new Function(readFileSync(nostrToolsBundle(), 'utf8') + '; return NostrTools;')();
window.NostrTools = nt;

await import('../../main/resources/static/js/replaceable-list.js');
await import('../../main/resources/static/js/block-list.js');

const BlockList = window.BlockList;

const USER = 'npub1test';
const RELAY = 'wss://relay.example';
const ALICE = 'b'.repeat(64);
const BOB = 'c'.repeat(64);
const CAROL = 'd'.repeat(64);

let SK_BYTES;
let SK_HEX;
let OWN_PUBKEY;
let published;
let cacheWrites;

function bytesToHex(bytes) {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}

function seal(tags) {
  return nt.nip44.encrypt(JSON.stringify(tags), nt.nip44.getConversationKey(SK_BYTES, OWN_PUBKEY));
}

function unseal(content) {
  return JSON.parse(nt.nip44.decrypt(content, nt.nip44.getConversationKey(SK_BYTES, OWN_PUBKEY)));
}

// A mute list as another client left it: two blocked keys and a muted hashtag,
// all sealed, plus a public tag this application never writes.
function existingList() {
  return {
    kind: 10000,
    pubkey: OWN_PUBKEY,
    created_at: 1000,
    tags: [['alt', 'mute list']],
    content: seal([['p', ALICE], ['p', BOB], ['t', 'spoilers']])
  };
}

function fakePool(events) {
  return {
    ensureRelay: () => Promise.resolve({
      subscribe: (filters, params) => {
        (events || []).forEach((e) => params.onevent(e));
        params.oneose();
        return { close: () => {} };
      }
    })
  };
}

function installApp(cached) {
  published = [];
  cacheWrites = [];
  window.APP = {
    getIdentityUserId: () => USER,
    loadIdentity: () => ({ userId: USER, pubkeyHex: OWN_PUBKEY }),
    effectiveReadRelays: () => Promise.resolve([RELAY]),
    effectiveRelays: () => Promise.resolve([RELAY]),
    ensureUnlocked: () => Promise.resolve(SK_HEX),
    loadBlockList: () => cached || [],
    saveBlockList: (userId, list) => cacheWrites.push(list)
  };
  window.NostrCrypto = { signEvent: (unsigned) => ({ ...unsigned, sig: 'sig' }) };
  window.NostrPublish = {
    buildReplaceableListEvent: (kind, tags, content) => ({ kind, created_at: 1, tags, content }),
    publish: (pool, urls, signed) => {
      published.push(signed);
      return Promise.resolve(urls.map((u) => ({ url: u, accepted: true })));
    }
  };
  window.NostrTools = nt;
}

beforeEach(() => {
  SK_BYTES = nt.generateSecretKey();
  SK_HEX = bytesToHex(SK_BYTES);
  OWN_PUBKEY = nt.getPublicKey(SK_BYTES);
  installApp();
});

describe('NIP-44 sealing to oneself', () => {
  // The conversation key is derived from the user's own secret and own public key,
  // which is the documented idiom for a private list entry.
  it('round-trips a tag array through the real nip44', () => {
    const tags = [['p', ALICE], ['t', 'spoilers']];

    expect(unseal(seal(tags))).toEqual(tags);
  });
});

describe('BlockList.block', () => {
  it('adds the key to the sealed entries', async () => {
    await BlockList.block(USER, CAROL, fakePool([existingList()]));

    const entries = unseal(published[0].content);
    expect(entries.filter((t) => t[0] === 'p').map((t) => t[1])).toEqual([ALICE, BOB, CAROL]);
  });

  // FR-007 and SC-005: with everything the user publishes in hand, an observer must
  // not be able to tell whom they blocked. Asserted on the event handed to signEvent,
  // because that is what gets published - a return value would prove nothing.
  it('publishes no blocked key in the clear', async () => {
    await BlockList.block(USER, CAROL, fakePool([existingList()]));

    const wire = JSON.stringify(published[0].tags);
    expect(wire).not.toContain(CAROL);
    expect(wire).not.toContain(ALICE);
    expect(published[0].tags.filter((t) => t[0] === 'p')).toEqual([]);
  });

  // A muted hashtag is an entry this application does not offer and must not delete.
  it('preserves muted entries of a kind it does not offer', async () => {
    await BlockList.block(USER, CAROL, fakePool([existingList()]));

    expect(unseal(published[0].content)).toContainEqual(['t', 'spoilers']);
  });

  // Public tags belong to whichever client wrote them.
  it('preserves the public tags it did not write', async () => {
    await BlockList.block(USER, CAROL, fakePool([existingList()]));

    expect(published[0].tags).toContainEqual(['alt', 'mute list']);
  });

  // THE GUARD. A list that will not decrypt is unreadable in the sense that matters:
  // replacing it discards every key the user has blocked.
  it('refuses to publish over a list that will not decrypt', async () => {
    const corrupt = { ...existingList(), content: 'not-decryptable-ciphertext' };

    await expect(BlockList.block(USER, CAROL, fakePool([corrupt])))
      .rejects.toMatchObject({ code: 'unreadable' });
    expect(published).toEqual([]);
  });

  // A NIP-04-era list, or one sealed to a different key, is equally unreadable.
  it('refuses when the list was sealed to another key', async () => {
    const otherSk = nt.generateSecretKey();
    const foreign = {
      ...existingList(),
      content: nt.nip44.encrypt('[["p","' + ALICE + '"]]',
        nt.nip44.getConversationKey(otherSk, nt.getPublicKey(otherSk)))
    };

    await expect(BlockList.block(USER, CAROL, fakePool([foreign])))
      .rejects.toMatchObject({ code: 'unreadable' });
    expect(published).toEqual([]);
  });

  it('publishes nothing when the key is already blocked', async () => {
    const result = await BlockList.block(USER, ALICE, fakePool([existingList()]));

    expect(result.unchanged).toBe(true);
    expect(published).toEqual([]);
  });

  it('starts a list when the relays confirm there is none', async () => {
    await BlockList.block(USER, CAROL, fakePool([]));

    expect(unseal(published[0].content)).toEqual([['p', CAROL]]);
    expect(published[0].tags).toEqual([]);
  });

  it('writes the cache only after a confirmed publish', async () => {
    await BlockList.block(USER, CAROL, fakePool([existingList()]));

    expect(cacheWrites).toEqual([[ALICE, BOB, CAROL]]);
  });

  it('refuses a malformed pubkey without publishing', async () => {
    await expect(BlockList.block(USER, 'nope', fakePool([existingList()]))).rejects.toThrow();
    expect(published).toEqual([]);
  });
});

describe('BlockList.unblock', () => {
  it('removes only the target entry', async () => {
    await BlockList.unblock(USER, ALICE, fakePool([existingList()]));

    const entries = unseal(published[0].content);
    expect(entries.filter((t) => t[0] === 'p').map((t) => t[1])).toEqual([BOB]);
    expect(entries).toContainEqual(['t', 'spoilers']);
  });

  it('publishes nothing when the key is not blocked', async () => {
    const result = await BlockList.unblock(USER, CAROL, fakePool([existingList()]));

    expect(result.unchanged).toBe(true);
    expect(published).toEqual([]);
  });
});

describe('BlockList.current and cached', () => {
  it('reports the blocked keys after decrypting', async () => {
    const result = await BlockList.current(USER, fakePool([existingList()]));

    expect(result.readable).toBe(true);
    expect(result.pubkeys).toEqual([ALICE, BOB]);
  });

  // The settings page must be able to say "could not be read" rather than showing
  // an empty list, which would read as "you have blocked nobody".
  it('reports unreadable rather than empty when the list will not decrypt', async () => {
    const corrupt = { ...existingList(), content: 'garbage' };

    const result = await BlockList.current(USER, fakePool([corrupt]));

    expect(result.readable).toBe(false);
    expect(result.pubkeys).toEqual([]);
  });

  it('reads the stored list synchronously without touching the network', () => {
    installApp([ALICE]);

    expect(BlockList.cached(USER)).toEqual([ALICE]);
  });
});
