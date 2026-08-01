import { describe, it, expect } from 'vitest';
import NostrPublish from '../../main/resources/static/js/nostr-publish.js';

describe('buildProfileEvent', () => {
  // kind-0 content omits empty fields and derives `name` from the nip05 local part.
  it('omits empty fields and derives name from nip05', () => {
    const ev = NostrPublish.buildProfileEvent({
      display_name: 'Alice', about: '', picture: 'https://x/y.png',
      banner: '', nip05: 'alice@bottin.example.com', lud16: '', website: '',
    });
    expect(ev.kind).toBe(0);
    expect(ev.tags).toEqual([]);
    expect(JSON.parse(ev.content)).toEqual({
      name: 'alice', display_name: 'Alice',
      picture: 'https://x/y.png', nip05: 'alice@bottin.example.com',
    });
  });

  // With no nip05 there is no `name` key.
  it('omits name when nip05 is absent', () => {
    const ev = NostrPublish.buildProfileEvent({ display_name: 'Bob' });
    expect(JSON.parse(ev.content)).toEqual({ display_name: 'Bob' });
  });
});

describe('buildRelayListEvent / relaysToTags', () => {
  // NIP-65 markers: both -> no marker, read-only -> "read", write-only -> "write".
  it('builds r tags with correct markers', () => {
    const ev = NostrPublish.buildRelayListEvent([
      { url: 'wss://a', read: true, write: true },
      { url: 'wss://b', read: true, write: false },
      { url: 'wss://c', read: false, write: true },
      { url: 'wss://d', read: false, write: false },
    ]);
    expect(ev.kind).toBe(10002);
    expect(ev.content).toBe('');
    expect(ev.tags).toEqual([
      ['r', 'wss://a'],
      ['r', 'wss://b', 'read'],
      ['r', 'wss://c', 'write'],
    ]);
  });
});

describe('publish', () => {
  // Each relay's promise maps to an accepted/reason result via allSettled.
  it('reports per-relay accepted and rejected results', async () => {
    const pool = {
      publish: (urls) => urls.map((u, i) =>
        i === 0 ? Promise.resolve('ok') : Promise.reject(new Error('nope'))),
    };
    const results = await NostrPublish.publish(pool, ['wss://a', 'wss://b'], { id: 'x' });
    expect(results).toEqual([
      { url: 'wss://a', accepted: true, reason: null },
      { url: 'wss://b', accepted: false, reason: 'Error: nope' },
    ]);
  });
});
