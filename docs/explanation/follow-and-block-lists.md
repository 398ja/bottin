# Follow and Block Lists

Why following and blocking are published to the user's own relays rather than
stored by this deployment, why blocks are encrypted while follows are not, and why
a list that cannot be read is never overwritten.

## The server cannot follow anyone

A follow is a signed Nostr event. So is a block. The signature must come from the
user's own private key, and that key never leaves their browser: it is held
encrypted in `localStorage`, decrypted into `sessionStorage` when they unlock it,
and used there to sign.

The server therefore has nothing to sign with. It can validate that a string looks
like a public key and answer `200`, but it cannot produce the event that makes the
follow real. For a while it did exactly that — `POST /api/v1/follow` returned
`{"status":"followed"}` and stored nothing — which is worse than not having the
endpoint at all, because it reports success for work never done.

Those routes are gone. Following and blocking are composed, signed and published
entirely in the browser, the same way the relay list at `/settings/relays` already
worked.

## The lists belong to the user, not to this directory

A follow made here is a [NIP-02](https://github.com/nostr-protocol/nips/blob/master/02.md)
contact list published to the user's relays. Sign into any other Nostr client with
the same key and the follow is already there. Nothing about it is specific to this
deployment, and nothing is lost if this deployment disappears.

That is the difference between a directory and a walled garden, and it is why the
list is not a table here. A row in our database would be ours. An event on their
relays is theirs.

The same holds for blocks, published as a
[NIP-51](https://github.com/nostr-protocol/nips/blob/master/51.md) mute list.

## Blocks are private; follows are not

Who you follow is public by the protocol's design, and treating it otherwise would
be pretending to a secrecy we could not keep.

Blocking is different. A public blocklist is a durable, unretractable statement
about another person, published under your name — and this directory ties keys to
real registered handles, so naming someone in it names a person. Most people
reasonably expect blocking to be a private act.

So the blocked keys are sealed with
[NIP-44](https://github.com/nostr-protocol/nips/blob/master/44.md) to the user's
own key, using a conversation key derived from their own secret and their own
public key. The mute list's public tags never carry a blocked key. An observer with
everything the user has ever published can see *that* they keep a mute list, and
nothing about who is on it.

This has a cost worth naming: a list only you can read is a list only you can
check. That is why `/settings/blocks` exists and asks for your passphrase — a
private list with no way to read it back would be write-only, and a block made by
mistake would be uncorrectable.

## A list that cannot be read is never overwritten

Both kinds of list are *replaceable* events: publishing one replaces the previous
one entirely. There is no "add one entry" operation. Every change is: read the
current list, merge, publish the whole thing back.

That makes the read safety-critical. If the read comes back empty because no relay
answered, and we publish anyway, we have just replaced a list of four hundred
people with a list of one — silently, network-wide, on a click the user thought
was harmless.

So "the relays told us the list is empty" must be distinguishable from "no relay
answered", and the client refuses to publish on the second. Refusing a follow is a
visible annoyance the user can retry. Erasing a follow list is neither visible nor
recoverable.

Getting that distinction is harder than it sounds, and the details are recorded in
`specs/007-follow-block-lists/research.md`. In short, the obvious ways to ask —
`querySync`, or a pool-level end-of-stored-events callback — both report success
when every relay failed to connect, which inverts the exact property the guard
exists to enforce.

The encrypted mute list has a second way to be unreadable: content that will not
decrypt, whether from a key mismatch, corruption, or an older client that wrote it
with a superseded scheme. Replacing that would discard every blocked key, so it is
refused for the same reason.

## What the browser remembers, and what it is allowed to decide

Each device caches a copy of both lists so a button can be labelled *Following* or
*Follow* without waiting on a relay — search would otherwise cost a round-trip per
keystroke.

That cache is written only after a relay has confirmed a change, and it is never
consulted for what gets published. Every change re-reads from the relays first. A
stale cache can therefore mislabel a button, which the next read corrects; it can
never influence the list itself.

## What this does not do

Blocking hides someone from your view. It does not prevent them from seeing you,
and no list held by you could — their client decides what to show them.

Other applications honour these lists by convention. What is guaranteed here is
what gets published, not what any third-party client chooses to do with it.

## Related

- [Architecture Overview](architecture.md)
