// Reads and republishes the replaceable list events a user owns: their NIP-02
// contact list (kind 3) and their NIP-51 mute list (kind 10000). Knows that both
// are replaceable and nothing about what either means - the codecs supply that.
//
// Spec: https://github.com/nostr-protocol/nips/blob/master/01.md
//   REQ subscriptions, tag filters and EOSE handling follow NIP-01.
//
// WHY THIS DOES NOT USE querySync OR pool.subscribeMany
//
// Publishing a replaceable event replaces the whole list. So a read that cannot be
// trusted must not be followed by a publish, and "the relays hold nothing" has to be
// distinguishable from "no relay answered". Neither convenient API can do that:
//
//   - pool.querySync resolves [] for both cases. No signal at all.
//   - pool.subscribeMany's oneose fires even when every relay failed to connect:
//     handleClose calls handleEose before recording the close (nostr-tools.js:3253),
//     so the tally completes and oneose fires with nothing read.
//   - Even per subscription, oneose fires from a timer as readily as from a real EOSE
//     frame - fire() arms setTimeout(receivedEose, eoseTimeout) (nostr-tools.js:3158).
//
// So each relay is driven directly: ensureRelay rejects on a failed connection, and
// eoseTimeout is set far beyond our own deadline, so the library's timer cannot fire
// first and an oneose inside our window can only be a real EOSE from a live relay.
//
// See specs/007-follow-block-lists/research.md R1. Shortening this to querySync or to
// pool-level oneose reintroduces a silent, unrecoverable data-loss bug.
var ReplaceableList = (function () {
    // Our own deadline for a read. Must stay well below EOSE_TIMEOUT_MS - the gap is
    // the mechanism, not a tuning value.
    var READ_DEADLINE_MS = 4000;

    // Handed to relay.subscribe. Deliberately far beyond READ_DEADLINE_MS so the
    // library's internal EOSE timer can never fire inside our window.
    var EOSE_TIMEOUT_MS = 30000;

    // Bounds the publish so a relay that never answers counts as not-accepted rather
    // than leaving the caller without a stated outcome.
    var PUBLISH_DEADLINE_MS = 4000;

    function error(code, message) {
        var e = new Error(message);
        e.code = code;
        return e;
    }

    // Resolves {event, readable}. `readable: false` means the read failed; it never
    // means the list is empty, and a caller must not publish on it.
    function read(userId, kind, injectedPool) {
        var app = window.APP;
        var identity = app.loadIdentity(userId);
        if (!identity || !identity.pubkeyHex) {
            return Promise.resolve({ event: null, readable: false });
        }

        return app.effectiveReadRelays(userId).then(function (relayUrls) {
            if (!relayUrls || !relayUrls.length) {
                return { event: null, readable: false };
            }
            var pool = injectedPool || new window.NostrTools.SimplePool();
            return readFrom(pool, relayUrls, kind, identity.pubkeyHex);
        });
    }

    function readFrom(pool, relayUrls, kind, pubkeyHex) {
        return new Promise(function (resolve) {
            var filters = [{ kinds: [kind], authors: [pubkeyHex] }];
            var subscriptions = [];
            var newest = null;
            var answered = false;
            var pending = relayUrls.length;
            var settled = false;
            var deadline = setTimeout(finish, READ_DEADLINE_MS);

            function finish() {
                if (settled) return;
                settled = true;
                clearTimeout(deadline);
                subscriptions.forEach(function (sub) {
                    try { sub.close(); } catch (ignored) { /* already closed */ }
                });
                resolve({ event: newest, readable: answered });
            }

            // Called when a relay has said all it is going to. A connection failure
            // counts as done but never as answered: it is precisely no information.
            function relayDone() {
                pending -= 1;
                if (pending === 0) finish();
            }

            relayUrls.forEach(function (url) {
                pool.ensureRelay(url).then(function (relay) {
                    if (settled) return;
                    subscriptions.push(relay.subscribe(filters, {
                        eoseTimeout: EOSE_TIMEOUT_MS,
                        onevent: function (evt) {
                            if (!newest || evt.created_at > newest.created_at) newest = evt;
                        },
                        oneose: function () {
                            answered = true;
                            relayDone();
                        },
                        onclose: function () { /* a relay that drops proves nothing */ }
                    }));
                }).catch(function () {
                    relayDone();
                });
            });
        });
    }

    // Reads the list, merges via `apply`, and republishes it.
    //
    // The order is load-bearing and is not an implementation detail:
    //   1. write relays  - refuse early, before any wait or any prompt (FR-018)
    //   2. read          - refuse if it cannot be trusted (FR-013, FR-014)
    //   3. unlock        - before decode, because kind 10000 needs the key to read
    //                      its own content
    //   4. decode        - may itself refuse, if the content will not decrypt
    //   5. apply         - returning null means nothing to do; publish nothing
    //   6. encode, sign, publish
    //
    // `apply` receives the decoded entries and returns the merged entries, or null.
    function mutate(userId, spec, apply, injectedPool) {
        var app = window.APP;
        var identity = app.loadIdentity(userId);
        if (!identity || !identity.pubkeyHex) {
            return Promise.reject(error('no_identity', 'No identity is signed in'));
        }
        var pool = injectedPool || new window.NostrTools.SimplePool();

        return app.effectiveRelays(userId, 'write').then(function (writeRelays) {
            if (!writeRelays || !writeRelays.length) {
                throw error('no_write_relays', 'No write relay is configured');
            }

            return read(userId, spec.kind, pool).then(function (found) {
                if (!found.readable) {
                    throw error('unreadable', 'The list could not be read');
                }

                return app.ensureUnlocked(userId).then(function (hexKey) {
                    var entries = spec.decode(found.event, hexKey, identity.pubkeyHex);
                    var merged = apply(entries);
                    if (merged === null) {
                        return { published: 0, of: 0, entries: entries, unchanged: true };
                    }

                    var unsigned = spec.encode(found.event, merged, hexKey, identity.pubkeyHex);
                    var signed = window.NostrCrypto.signEvent(
                        supersede(unsigned, found.event), hexKey);
                    return publishBounded(pool, writeRelays, signed).then(function (results) {
                        var accepted = results.filter(function (r) { return r.accepted; }).length;
                        return {
                            published: accepted,
                            of: results.length,
                            entries: merged,
                            unchanged: false
                        };
                    });
                });
            });
        });
    }

    // Guarantees the replacement outranks what it replaces.
    //
    // created_at is in whole seconds, so two edits inside the same second tie. NIP-01
    // breaks a tie on replaceable events by keeping the LOWEST event id, which is
    // unrelated to which the user meant - so a follow immediately undone could leave
    // the follow standing and the undo discarded. Observed against strfry: a follow
    // and an unfollow in the same second left the relay holding the follow.
    //
    // Stepping past the previous timestamp costs nothing and removes the tie. It can
    // put created_at slightly ahead of the wall clock, which is correct: it describes
    // this list's ordering, not the time of day.
    function supersede(unsigned, previous) {
        if (previous && unsigned.created_at <= previous.created_at) {
            unsigned.created_at = previous.created_at + 1;
        }
        return unsigned;
    }

    // A relay that never answers must not leave the caller without an outcome, so the
    // publish is bounded and anything unresolved by then counts as not-accepted.
    function publishBounded(pool, writeRelays, signed) {
        var timedOut = writeRelays.map(function (url) {
            return { url: url, accepted: false, reason: 'no response within ' + PUBLISH_DEADLINE_MS + 'ms' };
        });
        return Promise.race([
            window.NostrPublish.publish(pool, writeRelays, signed),
            new Promise(function (resolve) { setTimeout(function () { resolve(timedOut); }, PUBLISH_DEADLINE_MS); })
        ]);
    }

    return {
        read: read,
        mutate: mutate,
        error: error,
        READ_DEADLINE_MS: READ_DEADLINE_MS,
        PUBLISH_DEADLINE_MS: PUBLISH_DEADLINE_MS
    };
})();

window.ReplaceableList = ReplaceableList;
