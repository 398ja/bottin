import { describe, it, expect, beforeEach } from 'vitest';
import '../../main/resources/static/js/replaceable-list.js';
import '../../main/resources/static/js/follow-list.js';

const FollowList = window.FollowList;

const USER = 'npub1test';
const OWN_PUBKEY = 'a'.repeat(64);
const ALICE = 'b'.repeat(64);
const BOB = 'c'.repeat(64);
const CAROL = 'd'.repeat(64);
const RELAY = 'wss://relay.example';

let published;
let cacheWrites;

// The existing contact list as another client left it: a petname and a relay hint on
// one entry, a topic tag this application does not understand, and a content blob
// holding the relay JSON that kind-3 historically carried.
function existingList() {
  return {
    kind: 3,
    pubkey: OWN_PUBKEY,
    created_at: 1000,
    tags: [
      ['p', ALICE, 'wss://alice.relay', 'Alice from work'],
      ['p', BOB],
      ['t', 'nostr']
    ],
    content: '{"wss://old.relay":{"read":true,"write":true}}'
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
    ensureUnlocked: () => Promise.resolve('ff'.repeat(32)),
    loadFollowList: () => cached || [],
    saveFollowList: (userId, list) => cacheWrites.push(list)
  };
  window.NostrCrypto = { signEvent: (unsigned) => ({ ...unsigned, sig: 'sig' }) };
  window.NostrPublish = {
    buildReplaceableListEvent: (kind, tags, content) => ({ kind, created_at: 1, tags, content }),
    publish: (pool, urls, signed) => {
      published.push(signed);
      return Promise.resolve(urls.map((u) => ({ url: u, accepted: true })));
    }
  };
  window.NostrTools = { SimplePool: function () {} };
}

beforeEach(() => installApp());

describe('FollowList.follow', () => {
  // The follow is appended; everything already in the document is left alone.
  it('appends the new key', async () => {
    await FollowList.follow(USER, CAROL, fakePool([existingList()]));

    const tags = published[0].tags;
    expect(tags.filter((t) => t[0] === 'p').map((t) => t[1])).toEqual([ALICE, BOB, CAROL]);
  });

  // A petname and relay hint belong to whichever client wrote them. Rebuilding the
  // list from our own model would silently delete both.
  it('preserves a petname and relay hint on an existing entry', async () => {
    await FollowList.follow(USER, CAROL, fakePool([existingList()]));

    expect(published[0].tags[0]).toEqual(['p', ALICE, 'wss://alice.relay', 'Alice from work']);
  });

  // Tags of a kind this application does not offer, and the content blob, are not ours.
  it('preserves foreign tags and content', async () => {
    await FollowList.follow(USER, CAROL, fakePool([existingList()]));

    expect(published[0].tags).toContainEqual(['t', 'nostr']);
    expect(published[0].content).toBe('{"wss://old.relay":{"read":true,"write":true}}');
  });

  // Nothing to do is not an error, and must not republish an identical list.
  it('publishes nothing when the key is already followed', async () => {
    const result = await FollowList.follow(USER, ALICE, fakePool([existingList()]));

    expect(result.unchanged).toBe(true);
    expect(published).toEqual([]);
  });

  // The cache labels buttons. It is written only once the network has confirmed,
  // so a failed publish never leaves the page claiming a follow that does not exist.
  it('writes the cache only after a confirmed publish', async () => {
    await FollowList.follow(USER, CAROL, fakePool([existingList()]));

    expect(cacheWrites).toHaveLength(1);
    expect(cacheWrites[0]).toEqual([ALICE, BOB, CAROL]);
  });

  it('does not write the cache when no relay accepted', async () => {
    window.NostrPublish.publish = (pool, urls, signed) => {
      published.push(signed);
      return Promise.resolve(urls.map((u) => ({ url: u, accepted: false })));
    };

    const result = await FollowList.follow(USER, CAROL, fakePool([existingList()]));

    expect(result.published).toBe(0);
    expect(cacheWrites).toEqual([]);
  });

  // An `unchanged` result means the read succeeded and the list already held the key,
  // so those entries are the confirmed current list. Skipping the cache here left it
  // stale, and the control's label reverted on the next render.
  it('reconciles a stale cache when the list already held the key', async () => {
    installApp([]);

    const result = await FollowList.follow(USER, ALICE, fakePool([existingList()]));

    expect(result.unchanged).toBe(true);
    expect(published).toEqual([]);
    expect(cacheWrites).toEqual([[ALICE, BOB]]);
  });

  it('refuses a malformed pubkey without publishing', async () => {
    await expect(FollowList.follow(USER, 'not-a-key', fakePool([existingList()]))).rejects.toThrow();
    expect(published).toEqual([]);
  });

  // The list is published to a first-time follower who genuinely has none.
  it('starts a list when the relays confirm there is none', async () => {
    await FollowList.follow(USER, CAROL, fakePool([]));

    expect(published[0].tags).toEqual([['p', CAROL]]);
  });
});

describe('FollowList.unfollow', () => {
  it('removes only the target entry', async () => {
    await FollowList.unfollow(USER, ALICE, fakePool([existingList()]));

    const tags = published[0].tags;
    expect(tags.filter((t) => t[0] === 'p').map((t) => t[1])).toEqual([BOB]);
    expect(tags).toContainEqual(['t', 'nostr']);
  });

  it('publishes nothing when the key is not followed', async () => {
    const result = await FollowList.unfollow(USER, CAROL, fakePool([existingList()]));

    expect(result.unchanged).toBe(true);
    expect(published).toEqual([]);
  });
});

describe('FollowList.cached', () => {
  // Synchronous and network-free: search labels a control per row without a relay
  // round-trip per keystroke.
  it('reads the stored list without touching the network', () => {
    installApp([ALICE, BOB]);

    expect(FollowList.cached(USER)).toEqual([ALICE, BOB]);
  });

  it('answers an empty list when nothing is stored', () => {
    installApp(null);

    expect(FollowList.cached(USER)).toEqual([]);
  });
});

describe('FollowList.current', () => {
  it('reports the followed keys and that the read succeeded', async () => {
    const result = await FollowList.current(USER, fakePool([existingList()]));

    expect(result.readable).toBe(true);
    expect(result.pubkeys).toEqual([ALICE, BOB]);
  });

  // An unreadable read is reported as such rather than as an empty follow list, so
  // the settings page can say so instead of claiming the user follows nobody.
  it('reports unreadable rather than an empty list when no relay answers', async () => {
    const result = await FollowList.current(USER, { ensureRelay: () => Promise.reject(new Error('down')) });

    expect(result.readable).toBe(false);
    expect(result.pubkeys).toEqual([]);
  });
});
