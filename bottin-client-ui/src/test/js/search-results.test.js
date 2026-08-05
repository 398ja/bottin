import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

// The search behaviour lives in an inline <script> in search.html rather than
// in a file under static/js, so it is extracted from the real template and
// executed here. Restating the logic in the test instead would prove only that
// the copy works.
const template = readFileSync(
  path.resolve('src/main/resources/templates/search.html'), 'utf8');
const inlineScript = template.match(/<script>([\s\S]*?)<\/script>/)[1];

const ALICE = {
  pubkey: '3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d',
  name: 'alice',
  nip05: 'alice@example.test'
};

function renderPage() {
  document.body.innerHTML = `
    <input type="text" id="search-input">
    <div id="search-results"></div>
    <div id="search-loading" style="display: none;"></div>`;
}

let toasts;
let followCalls;
let followResult;

// Runs the template's script against the page. Debounce is collapsed to a
// direct call so a keystroke reaches fetch without waiting out 300ms.
//
// `identity` null models a signed-out visitor, for whom no control is offered.
function runSearchScript(options) {
  const opts = options || {};
  toasts = [];
  followCalls = [];
  followResult = opts.followResult || { published: 1, of: 1, unchanged: false };

  global.APP = {
    debounce: (fn) => fn,
    showToast: (message, level) => toasts.push({ message, level }),
    getIdentityUserId: () => ('identity' in opts ? opts.identity : 'npub1me')
  };
  window.APP = global.APP;

  const action = (verb) => (userId, pubkey) => {
    followCalls.push({ verb, pubkey });
    return opts.rejectWith ? Promise.reject(opts.rejectWith) : Promise.resolve(followResult);
  };
  global.FollowList = window.FollowList = {
    cached: () => opts.cachedFollows || [],
    follow: action('follow'),
    unfollow: action('unfollow')
  };

  new Function(inlineScript)();
}

function followButton() {
  return results().querySelector('.search-result button');
}

function type(query) {
  const input = document.getElementById('search-input');
  input.value = query;
  input.dispatchEvent(new Event('input'));
}

function results() {
  return document.getElementById('search-results');
}

describe('search.html result rendering', () => {
  beforeEach(() => {
    renderPage();
    vi.restoreAllMocks();
  });

  // If this is ever empty the extraction regex has gone stale and every test
  // below would pass against an empty script.
  it('extracts the inline script from the template', () => {
    expect(inlineScript).toContain('/api/v1/search');
  });

  // A match becomes a link to that key's profile page carrying the full
  // identifier, domain included.
  it('renders a match as a link to the profile', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ query: 'alice', results: [ALICE], total: 1 })
    });
    runSearchScript();

    type('alice');

    await vi.waitFor(() => {
      // The anchor wraps only the navigating part of the row: a follow control
      // cannot legally live inside it.
      const link = results().querySelector('.search-result a.search-result-link');
      expect(link).not.toBeNull();
      expect(link.getAttribute('href')).toBe('/profile/' + ALICE.pubkey);
      expect(link.textContent).toContain('alice@example.test');
    });
  });

  // The point of the 502: an unreachable directory must not read as "nobody
  // matched". Break it by dropping the `if (!r.ok) throw` guard and this fails,
  // because the empty state renders instead.
  it('reports a failed search rather than an empty result when the directory is down', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.resolve({ error: 'DIRECTORY_UNAVAILABLE' })
    });
    runSearchScript();

    type('alice');

    await vi.waitFor(() => {
      expect(results().textContent).toContain('Search failed');
    });
    expect(results().textContent).not.toContain('No profiles found');
  });

  // A query the directory answered with nothing is a different statement from
  // a query it never answered, and says so.
  it('reports no profiles found when the directory answered with none', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ query: 'nobody', results: [], total: 0 })
    });
    runSearchScript();

    type('nobody');

    await vi.waitFor(() => {
      expect(results().textContent).toContain('No profiles found');
    });
    expect(results().textContent).not.toContain('Search failed');
  });
});

describe('search.html follow control', () => {
  beforeEach(() => {
    renderPage();
    vi.restoreAllMocks();
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ query: 'alice', results: [ALICE], total: 1 })
    });
  });

  async function search(options) {
    runSearchScript(options);
    type('alice');
    await vi.waitFor(() => expect(results().querySelector('.search-result')).not.toBeNull());
  }

  // A visitor with no identity cannot follow anyone, so no control is offered
  // rather than one that fails when pressed.
  it('offers no control to a signed-out visitor', async () => {
    await search({ identity: null });

    expect(followButton()).toBeNull();
  });

  it('offers Follow for a key the cache does not hold', async () => {
    await search();

    expect(followButton().textContent).toBe('Follow');
  });

  it('offers Following for a key the cache holds', async () => {
    await search({ cachedFollows: [ALICE.pubkey] });

    expect(followButton().textContent).toBe('Following');
  });

  it('follows the key and relabels on success', async () => {
    await search();

    followButton().click();

    await vi.waitFor(() => expect(followButton().textContent).toBe('Following'));
    expect(followCalls).toEqual([{ verb: 'follow', pubkey: ALICE.pubkey }]);
  });

  it('unfollows a key the cache holds', async () => {
    await search({ cachedFollows: [ALICE.pubkey] });

    followButton().click();

    await vi.waitFor(() => expect(followButton().textContent).toBe('Follow'));
    expect(followCalls).toEqual([{ verb: 'unfollow', pubkey: ALICE.pubkey }]);
  });

  // The refusal names the list that could not be read, and the label does not move:
  // claiming a follow that was never published is the failure this guards.
  it('reports an unreadable list and leaves the label alone', async () => {
    await search({ rejectWith: Object.assign(new Error('x'), { code: 'unreadable' }) });

    followButton().click();

    await vi.waitFor(() => expect(toasts).toHaveLength(1));
    expect(toasts[0].message).toContain('Could not read your follow list');
    expect(followButton().textContent).toBe('Follow');
  });

  it('reports having nowhere to publish', async () => {
    await search({ rejectWith: Object.assign(new Error('x'), { code: 'no_write_relays' }) });

    followButton().click();

    await vi.waitFor(() => expect(toasts).toHaveLength(1));
    expect(toasts[0].message).toContain('Add at least one write relay');
  });

  // A cancelled unlock is a decision, not a failure, and says nothing.
  it('says nothing when the unlock is cancelled', async () => {
    await search({ rejectWith: new Error('cancelled') });

    followButton().click();

    await vi.waitFor(() => expect(followButton().disabled).toBe(false));
    expect(toasts).toEqual([]);
    expect(followButton().textContent).toBe('Follow');
  });

  // A publish some relays refused is its own outcome, and the count is stated.
  it('states the count when only some relays accepted', async () => {
    await search({ followResult: { published: 2, of: 3, unchanged: false } });

    followButton().click();

    await vi.waitFor(() => expect(toasts).toHaveLength(1));
    expect(toasts[0].message).toBe('Following · published to 2 of 3 relays');
  });

  it('reports a total publish failure and leaves the label alone', async () => {
    await search({ followResult: { published: 0, of: 2, unchanged: false } });

    followButton().click();

    await vi.waitFor(() => expect(toasts).toHaveLength(1));
    expect(toasts[0].message).toBe('Publish failed on all relays');
    expect(followButton().textContent).toBe('Follow');
  });
});
