import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/list-feedback.js';
import '../../main/resources/static/js/profile-view.js';

const OWN_PUBKEY = 'a'.repeat(64);
const OTHER_PUBKEY = 'b'.repeat(64);

const STORED_IDENTITY = {
  userId: 'npub1own', pubkeyHex: OWN_PUBKEY, displayName: 'Me',
  nip05: 'me@imani.test', about: 'my bio', picture: 'https://cdn.test/me.png',
  banner: null, lud16: null, website: null
};

// The profile card as profile.html lays it out, optionally carrying the key of
// the profile being viewed the way /profile/{pubkey} renders it.
function renderPage(viewedPubkey) {
  document.body.innerHTML =
    '<img id="profile-banner-image" class="hidden" src="" alt="">' +
    '<img id="profile-avatar" src="/img/default-avatar.svg" alt="">' +
    '<div id="profile-name"></div>' +
    '<div id="profile-nip05"></div>' +
    '<p id="profile-about" class="hidden"></p>' +
    '<div class="profile-detail hidden" id="profile-website-row"><a id="profile-website"></a></div>' +
    '<div class="profile-detail hidden" id="profile-lud16-row"><span id="profile-lud16"></span></div>' +
    '<div id="profile-actions" class="hidden"></div>' +
    (viewedPubkey ? '<span id="profile-pubkey" hidden>' + viewedPubkey + '</span>' : '');
  document.dispatchEvent(new Event('DOMContentLoaded'));
}

function actionButton(label) {
  const buttons = Array.from(document.querySelectorAll('#profile-actions button'));
  return buttons.find((b) => b.textContent === label) || null;
}

// A browser with no identity of its own: nobody signed in here.
function signOut() {
  window.APP.getIdentityUserId = () => null;
  window.APP.loadIdentity = () => null;
}

function name() {
  return document.getElementById('profile-name').textContent;
}

describe('profile-view.js', () => {
  let fetched;

  beforeEach(() => {
    fetched = null;
    window.APP = {
      getIdentityUserId: () => STORED_IDENTITY.userId,
      loadIdentity: () => STORED_IDENTITY,
      saveIdentity: vi.fn(),
      safeImageUrl: (value) => (value && value.indexOf('https://') === 0 ? value : null),
      effectiveReadRelays: vi.fn(() => Promise.resolve(['wss://read']))
    };
    window.NostrTools = { SimplePool: function () { return { close: vi.fn() }; } };
    window.ProfileFetch = {
      fetch: vi.fn((pool, relays, pubkeyHex) => {
        fetched = { relays: relays, pubkeyHex: pubkeyHex };
        return Promise.resolve({ displayName: 'Alice', nip05: 'alice@imani.test', about: 'her bio' });
      })
    };
    window.FollowList = {
      cached: () => [],
      follow: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false })),
      unfollow: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
    };
    window.BlockList = {
      cached: () => [],
      block: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false })),
      unblock: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
    };
  });

  // Registration collects a handle and nothing else, so a user who has not yet
  // visited the profile page has no display name at all. They must read as their
  // handle rather than as an empty line, which is what the page would otherwise
  // show for every newly registered account.
  it('falls back to the handle when no display name has been published', () => {
    window.APP.loadIdentity = () => ({
      userId: 'npub1own', pubkeyHex: OWN_PUBKEY, nip05: 'newcomer@imani.test'
    });

    renderPage(null);

    expect(name()).toBe('newcomer@imani.test');
  });

  // Own profile: what this browser holds is the profile, and no relay is asked.
  it('renders the stored identity when no key is being viewed', () => {
    renderPage(null);

    expect(name()).toBe('Me');
    expect(document.getElementById('profile-nip05').textContent).toBe('me@imani.test');
    expect(window.ProfileFetch.fetch).not.toHaveBeenCalled();
  });

  // The whole point of the route: another key's page must show that key's
  // published profile, not the signed-in user's, which is what it did while
  // the view ignored the pubkey the controller passed it.
  it('renders the viewed key\'s published profile, read from the relays', async () => {
    renderPage(OTHER_PUBKEY);

    await vi.waitFor(() => expect(name()).toBe('Alice'));
    expect(fetched).toEqual({ relays: ['wss://read'], pubkeyHex: OTHER_PUBKEY });
    expect(document.getElementById('profile-about').textContent).toBe('her bio');
  });

  // Another key's profile is not this browser's to keep: the stored identity
  // belongs to the signed-in user alone.
  it('does not store what it read for another key', async () => {
    renderPage(OTHER_PUBKEY);

    await vi.waitFor(() => expect(name()).toBe('Alice'));
    expect(window.APP.saveIdentity).not.toHaveBeenCalled();
  });

  // An unreachable relay must leave the key on show rather than falling back to
  // the signed-in user, which would label a stranger's page with your own name.
  it('shows the abbreviated key when the profile cannot be read', async () => {
    window.ProfileFetch.fetch = vi.fn(() => Promise.resolve(null));

    renderPage(OTHER_PUBKEY);

    await vi.waitFor(() => expect(name()).toBe('bbbbbbbb…bbbbbbbb'));
  });

  // Reaching your own profile by its key is still your profile.
  it('renders the stored identity when the viewed key is the user\'s own', () => {
    renderPage(OWN_PUBKEY);

    expect(name()).toBe('Me');
    expect(window.ProfileFetch.fetch).not.toHaveBeenCalled();
  });

  // The page is public. A reader who is not signed in has no relays of their own,
  // and resolves the deployment's the same way every other caller does.
  it('reads the profile for a visitor who is not signed in', async () => {
    signOut();

    renderPage(OTHER_PUBKEY);

    await vi.waitFor(() => expect(name()).toBe('Alice'));
    expect(fetched).toEqual({ relays: ['wss://read'], pubkeyHex: OTHER_PUBKEY });
  });

  // Nobody's own profile is on show when nobody is signed in: the page has no
  // key to render and must not fall over reaching for a stored identity.
  it('renders nothing on the own-profile page when nobody is signed in', () => {
    signOut();

    renderPage(null);

    expect(name()).toBe('');
    expect(window.ProfileFetch.fetch).not.toHaveBeenCalled();
  });
});

describe('profile-view.js follow control', () => {
  beforeEach(() => {
    window.APP = {
      getIdentityUserId: () => STORED_IDENTITY.userId,
      loadIdentity: () => STORED_IDENTITY,
      saveIdentity: vi.fn(),
      safeImageUrl: () => null,
      effectiveReadRelays: vi.fn(() => Promise.resolve(['wss://read'])),
      showToast: vi.fn()
    };
    window.NostrTools = { SimplePool: function () { return { close: vi.fn() }; } };
    window.ProfileFetch = { fetch: vi.fn(() => Promise.resolve({ displayName: 'Alice' })) };
    window.FollowList = {
      cached: () => [],
      follow: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false })),
      unfollow: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
    };
    window.BlockList = {
      cached: () => [],
      block: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false })),
      unblock: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
    };
  });

  // Nobody is signed in, so there is no key to follow with. A control that fails
  // when pressed is worse than none at all.
  it('offers no control to a signed-out reader', () => {
    signOut();

    renderPage(OTHER_PUBKEY);

    expect(document.querySelectorAll('#profile-actions button')).toHaveLength(0);
  });

  // Following yourself is not a thing, and the own-profile page returns before the
  // controls are ever considered.
  it('offers no control on one\'s own profile', () => {
    renderPage(null);

    expect(document.querySelectorAll('#profile-actions button')).toHaveLength(0);
  });

  it('offers Follow for a key the cache does not hold', () => {
    renderPage(OTHER_PUBKEY);

    expect(actionButton('Follow')).not.toBeNull();
  });

  it('offers Following for a key the cache holds', () => {
    window.FollowList.cached = () => [OTHER_PUBKEY];

    renderPage(OTHER_PUBKEY);

    expect(actionButton('Following')).not.toBeNull();
  });

  it('follows the viewed key and relabels', async () => {
    renderPage(OTHER_PUBKEY);

    actionButton('Follow').click();

    await vi.waitFor(() => expect(actionButton('Following')).not.toBeNull());
    expect(window.FollowList.follow).toHaveBeenCalledWith(STORED_IDENTITY.userId, OTHER_PUBKEY);
  });

  // An unreadable list must not leave the page claiming a follow that never
  // reached a relay.
  it('reports an unreadable list and leaves the label alone', async () => {
    window.FollowList.follow = vi.fn(() =>
      Promise.reject(Object.assign(new Error('x'), { code: 'unreadable' })));

    renderPage(OTHER_PUBKEY);
    actionButton('Follow').click();

    await vi.waitFor(() => expect(window.APP.showToast).toHaveBeenCalled());
    expect(window.APP.showToast.mock.calls[0][0]).toContain('Could not read your follow list');
    expect(actionButton('Follow')).not.toBeNull();
  });
});

describe('profile-view.js block control', () => {
  beforeEach(() => {
    window.APP = {
      getIdentityUserId: () => STORED_IDENTITY.userId,
      loadIdentity: () => STORED_IDENTITY,
      saveIdentity: vi.fn(),
      safeImageUrl: () => null,
      effectiveReadRelays: vi.fn(() => Promise.resolve(['wss://read'])),
      showToast: vi.fn()
    };
    window.NostrTools = { SimplePool: function () { return { close: vi.fn() }; } };
    window.ProfileFetch = { fetch: vi.fn(() => Promise.resolve({ displayName: 'Alice' })) };
    window.FollowList = {
      cached: () => [],
      follow: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false })),
      unfollow: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
    };
    window.BlockList = {
      cached: () => [],
      block: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false })),
      unblock: vi.fn(() => Promise.resolve({ published: 1, of: 1, unchanged: false }))
    };
  });

  it('offers Block for a key the cache does not hold', () => {
    renderPage(OTHER_PUBKEY);

    expect(actionButton('Block')).not.toBeNull();
  });

  // FR-008. A blocked key's profile stays reachable and readable by direct link:
  // this page is the only route back from a block reached any other way, and hiding
  // it would strand the user with no way to undo what they did.
  it('keeps a blocked key\'s profile readable, marked, and offering Unblock', async () => {
    window.BlockList.cached = () => [OTHER_PUBKEY];

    renderPage(OTHER_PUBKEY);

    await vi.waitFor(() => expect(name()).toBe('Alice'));
    expect(document.getElementById('profile-actions').textContent).toContain('Blocked');
    expect(actionButton('Unblock')).not.toBeNull();
  });

  // A blocked key is not someone to follow, so the follow control gives way to the
  // one action that matters on that page.
  it('offers no follow control while the key is blocked', () => {
    window.BlockList.cached = () => [OTHER_PUBKEY];

    renderPage(OTHER_PUBKEY);

    expect(actionButton('Follow')).toBeNull();
  });

  it('blocks the viewed key and marks the profile', async () => {
    let blocked = [];
    window.BlockList.cached = () => blocked;
    window.BlockList.block = vi.fn(() => {
      blocked = [OTHER_PUBKEY];
      return Promise.resolve({ published: 1, of: 1, unchanged: false });
    });

    renderPage(OTHER_PUBKEY);
    actionButton('Block').click();

    await vi.waitFor(() => expect(actionButton('Unblock')).not.toBeNull());
    expect(window.BlockList.block).toHaveBeenCalledWith(STORED_IDENTITY.userId, OTHER_PUBKEY);
  });

  it('reports an unreadable block list and leaves the page unmarked', async () => {
    window.BlockList.block = vi.fn(() =>
      Promise.reject(Object.assign(new Error('x'), { code: 'unreadable' })));

    renderPage(OTHER_PUBKEY);
    actionButton('Block').click();

    await vi.waitFor(() => expect(window.APP.showToast).toHaveBeenCalled());
    expect(window.APP.showToast.mock.calls[0][0]).toContain('Could not read your block list');
    expect(actionButton('Unblock')).toBeNull();
  });
});
