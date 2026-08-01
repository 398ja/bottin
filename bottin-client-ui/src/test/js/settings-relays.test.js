import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/settings-relays.js';

const RelayEditor = window.RelayEditor;
const USER = 'npub1test';
const OWN_RELAY = 'wss://mine.example';
const SYSTEM_RELAY = 'ws://relay-a:7777';

let ownRelays;
let systemRelays;
let advertised;
let publishedTo;

beforeEach(() => {
  document.body.innerHTML = `
    <div id="read-relays"></div>
    <div id="write-relays"></div>
    <button id="publish-btn"></button>`;

  ownRelays = [{ url: OWN_RELAY, read: true, write: true }];
  systemRelays = [SYSTEM_RELAY];
  advertised = null;
  publishedTo = null;

  window.APP = {
    getIdentityUserId: () => USER,
    loadRelays: () => ownRelays,
    saveRelays: vi.fn(),
    systemRelays: vi.fn(() => Promise.resolve(systemRelays)),
    ensureUnlocked: vi.fn(() => Promise.resolve('hexkey')),
    showToast: vi.fn()
  };
  window.NostrPublish = {
    buildRelayListEvent: (relays) => { advertised = relays; return { kind: 10002 }; },
    publish: (pool, writeRelays) => { publishedTo = writeRelays; return Promise.resolve([{ accepted: true }]); }
  };
  window.NostrCrypto = { signEvent: (unsigned) => ({ ...unsigned, sig: 'sig' }) };
  window.NostrTools = { SimplePool: function () {} };
});

describe('relay settings page', () => {
  // System relays are applied to every publish but were never in the user's own
  // list, so they must not appear in a page that offers a remove button per row.
  it('renders only the relays the user added', () => {
    RelayEditor.init();

    const rendered = document.getElementById('write-relays').textContent;
    expect(rendered).toContain(OWN_RELAY);
    expect(rendered).not.toContain(SYSTEM_RELAY);
  });

  // A new user owns nothing yet; the page says so rather than listing relays
  // they cannot edit.
  it('shows the empty state when the user has added no relays', () => {
    ownRelays = [];

    RelayEditor.init();

    expect(document.getElementById('write-relays').textContent)
      .toContain('No write relays configured');
  });
});

describe('publishing the relay list', () => {
  // Events land on the system relays, so a kind-10002 omitting them would
  // advertise a list its own author's events cannot be found on.
  it('advertises the user relays together with the system relays', async () => {
    RelayEditor.init();

    await RelayEditor.publishRelays();

    expect(advertised.map((r) => r.url)).toEqual([OWN_RELAY, SYSTEM_RELAY]);
  });

  // The union is also where the event is sent, not just what it claims.
  it('publishes to the user relays together with the system relays', async () => {
    RelayEditor.init();

    await RelayEditor.publishRelays();

    expect(publishedTo).toEqual([OWN_RELAY, SYSTEM_RELAY]);
  });

  // A system relay the user also added themselves is one relay, not two.
  it('does not repeat a system relay the user already added', async () => {
    ownRelays = [{ url: SYSTEM_RELAY, read: true, write: true }];

    RelayEditor.init();
    await RelayEditor.publishRelays();

    expect(publishedTo).toEqual([SYSTEM_RELAY]);
  });

  // With nothing to publish to, the user is told rather than shown a silent no-op.
  it('reports when there is no write relay at all', async () => {
    ownRelays = [];
    systemRelays = [];

    RelayEditor.init();
    await RelayEditor.publishRelays();

    expect(window.APP.showToast)
      .toHaveBeenCalledWith('Add at least one write relay before publishing.', 'error');
    expect(publishedTo).toBeNull();
  });
});
