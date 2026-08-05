import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import '../../main/resources/static/js/replaceable-list.js';

const ReplaceableList = window.ReplaceableList;

const USER = 'npub1test';
const OWN_PUBKEY = 'a'.repeat(64);
const KIND = 3;

const RELAY_A = 'wss://a.example';
const RELAY_B = 'wss://b.example';

function event(created_at, tags) {
  return { kind: KIND, pubkey: OWN_PUBKEY, created_at, tags: tags || [], content: '' };
}

// A pool shaped like nostr-tools' SimplePool, but only as far as the read uses it:
// ensureRelay resolves or rejects per URL, and each relay's subscribe reports what
// that relay holds. `eose: false` models a relay that connects and then says nothing,
// which is the case the guard exists to catch.
//
// Deliberately does NOT model pool.subscribeMany. The read must not use it: its
// oneose fires even when every relay failed to connect (nostr-tools.js:3253), so a
// fake built around it would agree with a broken implementation.
function fakePool(relays) {
  return {
    ensureRelay: (url) => {
      const relay = relays[url];
      if (!relay) return Promise.reject(new Error('connection refused'));
      return Promise.resolve({
        subscribe: (filters, params) => {
          (relay.events || []).forEach((e) => params.onevent(e));
          if (relay.eose !== false) params.oneose();
          return { close: () => {} };
        }
      });
    }
  };
}

function installApp(readRelays) {
  window.APP = {
    getIdentityUserId: () => USER,
    loadIdentity: () => ({ userId: USER, pubkeyHex: OWN_PUBKEY }),
    effectiveReadRelays: () => Promise.resolve(readRelays)
  };
}

describe('ReplaceableList.read — the clobber guard', () => {
  beforeEach(() => {
    installApp([RELAY_A, RELAY_B]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // Every relay refusing the connection means nothing was read. Reporting that as an
  // empty list is what destroys a follow list: the caller publishes over it.
  it('reports unreadable when no relay can be reached', async () => {
    const result = await ReplaceableList.read(USER, KIND, fakePool({}));

    expect(result.readable).toBe(false);
    expect(result.event).toBeNull();
  });

  // A relay that connects and sends EOSE carrying nothing has genuinely told us it
  // holds nothing. That is the one case where publishing a fresh list is correct.
  it('reports readable when a relay confirms it holds nothing', async () => {
    const result = await ReplaceableList.read(USER, KIND, fakePool({ [RELAY_A]: { events: [] } }));

    expect(result.readable).toBe(true);
    expect(result.event).toBeNull();
  });

  // Relays disagree and retain different history, so the newest wins rather than
  // whichever answers first - as profile-fetch.js resolves kind 0.
  it('takes the newest event when relays hold different versions', async () => {
    const older = event(1000, [['p', 'b'.repeat(64)]]);
    const newer = event(2000, [['p', 'c'.repeat(64)]]);

    const result = await ReplaceableList.read(USER, KIND, fakePool({
      [RELAY_A]: { events: [older] },
      [RELAY_B]: { events: [newer] }
    }));

    expect(result.readable).toBe(true);
    expect(result.event.created_at).toBe(2000);
  });

  // A relay that accepts the connection and then never answers has told us nothing.
  // The library would call this EOSE once its own timer fired; the read must not.
  it('reports unreadable when a relay connects but never sends EOSE', async () => {
    vi.useFakeTimers();

    const promise = ReplaceableList.read(USER, KIND, fakePool({
      [RELAY_A]: { events: [], eose: false }
    }));
    await vi.advanceTimersByTimeAsync(5000);
    const result = await promise;

    expect(result.readable).toBe(false);
  });

  // One reachable relay is enough to trust the read; a dead sibling must not veto it.
  it('reports readable when one relay answers and another is unreachable', async () => {
    const held = event(1000, [['p', 'b'.repeat(64)]]);

    const result = await ReplaceableList.read(USER, KIND, fakePool({ [RELAY_A]: { events: [held] } }));

    expect(result.readable).toBe(true);
    expect(result.event.created_at).toBe(1000);
  });

  // No configured read relays is not an empty list either.
  it('reports unreadable when no read relays are configured', async () => {
    installApp([]);

    const result = await ReplaceableList.read(USER, KIND, fakePool({}));

    expect(result.readable).toBe(false);
  });
});

// ---------------------------------------------------------------------------

const FORTY = Array.from({ length: 40 }, (_, i) => ['p', String(i).padStart(64, '0')]);

// Passes entries through unchanged plus one appended tag, which is all these tests
// need of a codec. The real ones live in follow-list.js and block-list.js.
const passthroughSpec = {
  kind: KIND,
  decode: (evt) => (evt ? evt.tags.slice() : []),
  encode: (evt, tags) => window.NostrPublish.buildReplaceableListEvent(KIND, tags, evt ? evt.content : '')
};

let published;
let unlockCalls;
let decodeCalls;
let cacheWrites;
let publishResults;

function installMutateApp(options) {
  const opts = options || {};
  published = [];
  unlockCalls = [];
  decodeCalls = [];
  cacheWrites = [];

  window.APP = {
    getIdentityUserId: () => USER,
    loadIdentity: () => ({ userId: USER, pubkeyHex: OWN_PUBKEY }),
    effectiveReadRelays: () => Promise.resolve(opts.readRelays || [RELAY_A]),
    effectiveRelays: () => Promise.resolve('writeRelays' in opts ? opts.writeRelays : [RELAY_A]),
    ensureUnlocked: () => {
      unlockCalls.push(Date.now());
      return opts.unlockRejects ? Promise.reject(new Error('cancelled')) : Promise.resolve('ff'.repeat(32));
    },
    loadFollowList: () => opts.cached || [],
    saveFollowList: (userId, list) => cacheWrites.push(list),
    showToast: () => {}
  };
  window.NostrCrypto = { signEvent: (unsigned) => ({ ...unsigned, sig: 'sig' }) };
  window.NostrPublish = {
    buildReplaceableListEvent: (kind, tags, content) => ({ kind, created_at: 1, tags, content }),
    publish: (pool, urls, signed) => {
      published.push(signed);
      if (opts.publishNeverSettles) return new Promise(() => {});
      return Promise.resolve(publishResults || urls.map((u) => ({ url: u, accepted: true })));
    }
  };
  window.NostrTools = { SimplePool: function () {} };
}

describe('ReplaceableList.mutate', () => {
  beforeEach(() => {
    publishResults = null;
    installMutateApp();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // The whole point of the guard. A user following forty people whose relays are
  // unreachable must not end up publishing a list of one.
  it('refuses to publish over a list it could not read', async () => {
    installMutateApp();

    await expect(
      ReplaceableList.mutate(USER, passthroughSpec, (tags) => tags.concat([['p', 'z'.repeat(64)]]), fakePool({}))
    ).rejects.toMatchObject({ code: 'unreadable' });

    expect(published).toEqual([]);
  });

  // Same scenario stated as the loss it prevents: had it published, forty entries
  // would have become one.
  it('does not collapse a forty-entry list when the read fails', async () => {
    installMutateApp();

    await ReplaceableList.mutate(USER, passthroughSpec, () => [['p', 'z'.repeat(64)]], fakePool({}))
      .catch(() => {});

    expect(published).toEqual([]);
  });

  // kind 10000 cannot be decoded without the key, so the unlock has to come first.
  it('unlocks before decoding', async () => {
    const spec = {
      ...passthroughSpec,
      decode: (evt) => { decodeCalls.push(unlockCalls.length); return evt ? evt.tags.slice() : []; }
    };

    await ReplaceableList.mutate(USER, spec, (tags) => tags.concat([['p', 'z'.repeat(64)]]),
      fakePool({ [RELAY_A]: { events: [event(1, FORTY)] } }));

    expect(decodeCalls[0]).toBeGreaterThan(0);
  });

  // Nowhere to publish is refused before the relay read and before the passphrase
  // prompt: a user with no write relays must not be made to wait, nor asked for a
  // key that cannot be put to use.
  it('refuses with no write relays before reading or prompting', async () => {
    installMutateApp({ writeRelays: [] });

    await expect(
      ReplaceableList.mutate(USER, passthroughSpec, (tags) => tags, fakePool({ [RELAY_A]: { events: [] } }))
    ).rejects.toMatchObject({ code: 'no_write_relays' });

    expect(unlockCalls).toEqual([]);
    expect(published).toEqual([]);
  });

  // A no-op change publishes nothing rather than republishing an identical list.
  it('publishes nothing when apply reports no change', async () => {
    const result = await ReplaceableList.mutate(USER, passthroughSpec, () => null,
      fakePool({ [RELAY_A]: { events: [event(1, FORTY)] } }));

    expect(result.unchanged).toBe(true);
    expect(published).toEqual([]);
  });

  it('resolves with the accepted count on success', async () => {
    const result = await ReplaceableList.mutate(USER, passthroughSpec,
      (tags) => tags.concat([['p', 'z'.repeat(64)]]),
      fakePool({ [RELAY_A]: { events: [event(1, FORTY)] } }));

    expect(result.published).toBe(1);
    expect(result.of).toBe(1);
    expect(result.entries).toHaveLength(41);
  });

  // Some relays accepting and others refusing is its own outcome, distinguishable
  // from both total success and total failure.
  it('reports a partial publish as partial', async () => {
    installMutateApp({ writeRelays: [RELAY_A, RELAY_B] });
    publishResults = [{ url: RELAY_A, accepted: true }, { url: RELAY_B, accepted: false }];

    const result = await ReplaceableList.mutate(USER, passthroughSpec,
      (tags) => tags.concat([['p', 'z'.repeat(64)]]),
      fakePool({ [RELAY_A]: { events: [event(1, FORTY)] } }));

    expect(result.published).toBe(1);
    expect(result.of).toBe(2);
    expect(result.published).toBeGreaterThan(0);
    expect(result.published).toBeLessThan(result.of);
  });

  // The cache labels controls; it must never influence what is published. A populated
  // cache with an unreadable read still refuses - proving the cache is not consulted.
  it('never substitutes the cache for a failed read', async () => {
    installMutateApp({ cached: FORTY.map((t) => t[1]) });

    await expect(
      ReplaceableList.mutate(USER, passthroughSpec, (tags) => tags.concat([['p', 'z'.repeat(64)]]), fakePool({}))
    ).rejects.toMatchObject({ code: 'unreadable' });

    expect(published).toEqual([]);
  });

  // Cancelling the unlock is a decision, not a failure: nothing published, nothing cached.
  it('propagates a cancelled unlock without publishing or caching', async () => {
    installMutateApp({ unlockRejects: true });

    await expect(
      ReplaceableList.mutate(USER, passthroughSpec, (tags) => tags,
        fakePool({ [RELAY_A]: { events: [event(1, FORTY)] } }))
    ).rejects.toThrow();

    expect(published).toEqual([]);
    expect(cacheWrites).toEqual([]);
  });

  // created_at is in whole seconds, so two edits in the same second tie, and NIP-01
  // breaks the tie by keeping the lowest event id - not the later intent. Observed
  // against strfry: a follow and an unfollow in the same second left the relay
  // holding the follow. The replacement must therefore outrank what it replaces.
  it('publishes a timestamp strictly later than the event it replaces', async () => {
    const future = Math.floor(Date.now() / 1000) + 500;
    installMutateApp();
    window.NostrPublish.buildReplaceableListEvent = (kind, tags, content) =>
      ({ kind, created_at: Math.floor(Date.now() / 1000), tags, content });

    await ReplaceableList.mutate(USER, passthroughSpec, (tags) => tags.concat([['p', 'z'.repeat(64)]]),
      fakePool({ [RELAY_A]: { events: [{ ...event(future, FORTY) }] } }));

    expect(published[0].created_at).toBe(future + 1);
  });

  // The ordinary case leaves the clock alone.
  it('keeps the current timestamp when the previous event is older', async () => {
    const past = Math.floor(Date.now() / 1000) - 500;
    installMutateApp();
    const now = Math.floor(Date.now() / 1000);
    window.NostrPublish.buildReplaceableListEvent = (kind, tags, content) =>
      ({ kind, created_at: now, tags, content });

    await ReplaceableList.mutate(USER, passthroughSpec, (tags) => tags.concat([['p', 'z'.repeat(64)]]),
      fakePool({ [RELAY_A]: { events: [{ ...event(past, FORTY) }] } }));

    expect(published[0].created_at).toBe(now);
  });

  // SC-003: an outcome is always stated, even when nothing ever answers.
  it('resolves to a stated outcome within the bound when nothing answers', async () => {
    vi.useFakeTimers();
    installMutateApp({ publishNeverSettles: true });

    let settled = false;
    ReplaceableList.mutate(USER, passthroughSpec, (tags) => tags.concat([['p', 'z'.repeat(64)]]),
      fakePool({ [RELAY_A]: { events: [], eose: false } }))
      .then(() => { settled = true; }, () => { settled = true; });

    await vi.advanceTimersByTimeAsync(10000);

    expect(settled).toBe(true);
  });
});
