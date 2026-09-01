import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/list-feedback.js';
import '../../main/resources/static/js/profile-lookup.js';
import '../../main/resources/static/js/settings-lists.js';

const SettingsLists = window.SettingsLists;

const USER = 'npub1me';
const ALICE = 'b'.repeat(64);
const BOB = 'c'.repeat(64);

let querySyncCalls;
let toasts;

function renderPage(id) {
  document.body.innerHTML = '<div id="' + id + '"></div>';
}

function list(id) {
  return document.getElementById(id);
}

function rows(id) {
  return Array.from(list(id).querySelectorAll('.search-result'));
}

function installApp(options) {
  const opts = options || {};
  querySyncCalls = [];
  toasts = [];

  window.APP = {
    getIdentityUserId: () => ('identity' in opts ? opts.identity : USER),
    effectiveReadRelays: () => Promise.resolve(opts.readRelays || ['wss://relay']),
    // The real app.js guard, restated: only http(s) URLs survive it.
    safeImageUrl: (value) => (/^https?:\/\//.test(value || '') ? value : null),
    showToast: (message, level) => toasts.push({ message, level })
  };
  window.NostrTools = {
    SimplePool: function () {
      return {
        querySync: (relays, filter) => {
          querySyncCalls.push(filter);
          return Promise.resolve(opts.metadata || []);
        },
        close: () => {}
      };
    }
  };
  window.FollowList = {
    current: () => Promise.resolve(opts.follows || { pubkeys: [], readable: true }),
    unfollow: opts.unfollow || (() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
  };
  window.BlockList = {
    current: () => Promise.resolve(opts.blocks || { pubkeys: [], readable: true }),
    unblock: opts.unblock || (() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
  };
}

function metadataEvent(pubkey, name, created_at, extra) {
  const content = Object.assign({ display_name: name }, extra || {});
  return { kind: 0, pubkey, created_at: created_at || 1, content: JSON.stringify(content) };
}

describe('followed users page', () => {
  beforeEach(() => renderPage('follows-list'));

  // One query for the whole list, not one per row: a hundred follows must not mean a
  // hundred round-trips.
  it('resolves every name in a single batched query', async () => {
    installApp({
      follows: { pubkeys: [ALICE, BOB], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice'), metadataEvent(BOB, 'Bob')]
    });

    await SettingsLists.initFollows();

    expect(querySyncCalls).toHaveLength(1);
    expect(querySyncCalls[0].authors).toEqual([ALICE, BOB]);
    expect(list('follows-list').textContent).toContain('Alice');
    expect(list('follows-list').textContent).toContain('Bob');
  });

  // Somebody who has published no profile is still on the list; their key stands in
  // for a name they never published.
  it('shows an abbreviated key for someone with no published name', async () => {
    installApp({ follows: { pubkeys: [ALICE], readable: true }, metadata: [] });

    await SettingsLists.initFollows();

    expect(list('follows-list').textContent).toContain('bbbbbbbb…bbbbbbbb');
  });

  // The identifier a reader recognises is the NIP-05 address, not the key behind it.
  it('shows the NIP-05 identifier rather than the key', async () => {
    installApp({
      follows: { pubkeys: [ALICE], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice', 1, { nip05: 'alice@example.com' })]
    });

    await SettingsLists.initFollows();

    expect(list('follows-list').textContent).toContain('alice@example.com');
    expect(list('follows-list').textContent).not.toContain('bbbbbbbb…bbbbbbbb');
  });

  it('shows the published picture as the avatar', async () => {
    installApp({
      follows: { pubkeys: [ALICE], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice', 1, { picture: 'https://example.com/alice.png' })]
    });

    await SettingsLists.initFollows();

    expect(rows('follows-list')[0].querySelector('img').src).toBe('https://example.com/alice.png');
  });

  // A row with no image at all would collapse out of line with its neighbours.
  it('falls back to the placeholder avatar when no picture is published', async () => {
    installApp({ follows: { pubkeys: [ALICE], readable: true }, metadata: [] });

    await SettingsLists.initFollows();

    expect(rows('follows-list')[0].querySelector('img').getAttribute('src'))
      .toBe('/img/default-avatar.svg');
  });

  // The picture URL arrives from a relay, so it is whatever its author chose to
  // publish. Anything that is not http(s) is refused the same as none at all.
  it('refuses a picture URL that is not http(s)', async () => {
    installApp({
      follows: { pubkeys: [ALICE], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice', 1, { picture: 'javascript:alert(1)' })]
    });

    await SettingsLists.initFollows();

    expect(rows('follows-list')[0].querySelector('img').getAttribute('src'))
      .toBe('/img/default-avatar.svg');
  });

  it('takes the newest profile when relays disagree', async () => {
    installApp({
      follows: { pubkeys: [ALICE], readable: true },
      metadata: [metadataEvent(ALICE, 'Old', 100), metadataEvent(ALICE, 'Current', 200)]
    });

    await SettingsLists.initFollows();

    expect(list('follows-list').textContent).toContain('Current');
    expect(list('follows-list').textContent).not.toContain('Old');
  });

  it('says so plainly when the list is empty', async () => {
    installApp({ follows: { pubkeys: [], readable: true } });

    await SettingsLists.initFollows();

    expect(list('follows-list').textContent).toContain('You are not following anyone yet');
  });

  // "You follow nobody" is a claim about the list. A list that was never read cannot
  // support it, so the page says what actually happened.
  it('distinguishes an unreadable list from an empty one', async () => {
    installApp({ follows: { pubkeys: [], readable: false } });

    await SettingsLists.initFollows();

    expect(list('follows-list').textContent).toContain('could not be read');
    expect(list('follows-list').textContent).not.toContain('not following anyone');
  });

  it('removes the row once an unfollow is confirmed', async () => {
    installApp({
      follows: { pubkeys: [ALICE, BOB], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice'), metadataEvent(BOB, 'Bob')]
    });
    await SettingsLists.initFollows();

    rows('follows-list')[0].querySelector('button').click();

    await vi.waitFor(() => expect(rows('follows-list')).toHaveLength(1));
    expect(list('follows-list').textContent).toContain('Bob');
  });

  // A row that vanished without the change reaching a relay would tell the user they
  // had undone something they had not.
  it('keeps the row when the unfollow reaches no relay', async () => {
    installApp({
      follows: { pubkeys: [ALICE], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice')],
      unfollow: () => Promise.resolve({ published: 0, of: 2, unchanged: false })
    });
    await SettingsLists.initFollows();

    rows('follows-list')[0].querySelector('button').click();

    await vi.waitFor(() => expect(toasts).toHaveLength(1));
    expect(rows('follows-list')).toHaveLength(1);
  });

  // An unreachable relay costs the names, never the list itself.
  it('still lists the keys when names cannot be resolved', async () => {
    installApp({ follows: { pubkeys: [ALICE], readable: true }, readRelays: [] });

    await SettingsLists.initFollows();

    expect(rows('follows-list')).toHaveLength(1);
  });

  it('renders nothing when nobody is signed in', async () => {
    installApp({ identity: null });

    await SettingsLists.initFollows();

    expect(rows('follows-list')).toHaveLength(0);
  });
});

describe('blocked users page', () => {
  beforeEach(() => renderPage('blocks-list'));

  it('lists blocked keys with an unblock control', async () => {
    installApp({
      blocks: { pubkeys: [ALICE], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice')]
    });

    await SettingsLists.initBlocks();

    expect(list('blocks-list').textContent).toContain('Alice');
    expect(rows('blocks-list')[0].querySelector('button').textContent).toBe('Unblock');
  });

  it('says so plainly when nobody is blocked', async () => {
    installApp({ blocks: { pubkeys: [], readable: true } });

    await SettingsLists.initBlocks();

    expect(list('blocks-list').textContent).toContain('No blocked users');
  });

  // A mute list that will not decrypt reads as unreadable, never as "you have blocked
  // nobody" - which would invite the user to believe a block had been lost.
  it('distinguishes an undecipherable list from an empty one', async () => {
    installApp({ blocks: { pubkeys: [], readable: false } });

    await SettingsLists.initBlocks();

    expect(list('blocks-list').textContent).toContain('could not be read');
    expect(list('blocks-list').textContent).not.toContain('No blocked users');
  });

  it('removes the row once an unblock is confirmed', async () => {
    installApp({
      blocks: { pubkeys: [ALICE], readable: true },
      metadata: [metadataEvent(ALICE, 'Alice')]
    });
    await SettingsLists.initBlocks();

    rows('blocks-list')[0].querySelector('button').click();

    await vi.waitFor(() => expect(rows('blocks-list')).toHaveLength(0));
  });
});
