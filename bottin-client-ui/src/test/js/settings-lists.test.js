import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/list-feedback.js';
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

function metadataEvent(pubkey, name, created_at) {
  return { kind: 0, pubkey, created_at: created_at || 1, content: JSON.stringify({ display_name: name }) };
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
