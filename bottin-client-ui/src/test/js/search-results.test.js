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

// Runs the template's script against the page. Debounce is collapsed to a
// direct call so a keystroke reaches fetch without waiting out 300ms.
function runSearchScript() {
  global.APP = { debounce: (fn) => fn, showToast: vi.fn() };
  window.APP = global.APP;
  new Function(inlineScript)();
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
      const link = results().querySelector('a.search-result');
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
