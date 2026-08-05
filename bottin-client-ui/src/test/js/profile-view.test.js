import { describe, it, expect, beforeEach, vi } from 'vitest';
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
    (viewedPubkey ? '<span id="profile-pubkey" hidden>' + viewedPubkey + '</span>' : '');
  document.dispatchEvent(new Event('DOMContentLoaded'));
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
