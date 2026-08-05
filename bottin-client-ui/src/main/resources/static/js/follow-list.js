// Who the signed-in user follows, as a NIP-02 contact list.
//
// Spec: https://github.com/nostr-protocol/nips/blob/master/02.md
//
// Entries are the event's public tags: a follow is `["p", pubkey]`, optionally
// carrying a relay hint and a petname added by another client. The `content` field
// is not ours either - kind 3 historically holds a relay JSON blob written by other
// clients - so it is carried across untouched.
//
// This module is only a codec plus four verbs. Reading, the clobber guard, unlocking,
// signing and publishing all live in ReplaceableList, which owns them once so the
// guard has a single implementation.
var FollowList = (function () {
    var KIND = 3;
    var PUBKEY_PATTERN = /^[0-9a-f]{64}$/;

    function followedKeys(tags) {
        return tags
            .filter(function (tag) { return tag[0] === 'p' && tag[1]; })
            .map(function (tag) { return tag[1]; });
    }

    var spec = {
        kind: KIND,

        // Entries are the tags verbatim, foreign ones included, so the merge in
        // `apply` can leave everything it does not own exactly where it found it.
        decode: function (event) {
            return event ? event.tags.slice() : [];
        },

        encode: function (previous, tags) {
            return window.NostrPublish.buildReplaceableListEvent(
                KIND, tags, previous ? previous.content : '');
        }
    };

    function requirePubkey(pubkey) {
        if (!PUBKEY_PATTERN.test(pubkey || '')) {
            throw ReplaceableList.error('invalid_pubkey', '"' + pubkey + '" is not a Nostr public key');
        }
    }

    // Records the list locally once the network has confirmed it. `unchanged` counts:
    // it means the read succeeded and the list already held what was asked for, so
    // those entries are the confirmed current list. Skipping it would leave a stale
    // cache mislabelling the control, and the label would revert on the next render.
    function cacheOnConfirmed(userId, result) {
        if (result.published > 0 || result.unchanged) {
            window.APP.saveFollowList(userId, followedKeys(result.entries));
        }
        return result;
    }

    function follow(userId, pubkey, injectedPool) {
        try {
            requirePubkey(pubkey);
        } catch (e) {
            return Promise.reject(e);
        }
        return ReplaceableList.mutate(userId, spec, function (tags) {
            if (followedKeys(tags).indexOf(pubkey) !== -1) return null;
            return tags.concat([['p', pubkey]]);
        }, injectedPool).then(function (result) { return cacheOnConfirmed(userId, result); });
    }

    function unfollow(userId, pubkey, injectedPool) {
        try {
            requirePubkey(pubkey);
        } catch (e) {
            return Promise.reject(e);
        }
        return ReplaceableList.mutate(userId, spec, function (tags) {
            if (followedKeys(tags).indexOf(pubkey) === -1) return null;
            return tags.filter(function (tag) { return !(tag[0] === 'p' && tag[1] === pubkey); });
        }, injectedPool).then(function (result) { return cacheOnConfirmed(userId, result); });
    }

    // Reads the published list. Reports `readable` so a caller can tell "follows
    // nobody" from "could not be read" rather than showing one as the other.
    function current(userId, injectedPool) {
        return ReplaceableList.read(userId, KIND, injectedPool).then(function (found) {
            return {
                pubkeys: found.event ? followedKeys(found.event.tags) : [],
                readable: found.readable
            };
        });
    }

    function cached(userId) {
        return window.APP.loadFollowList(userId) || [];
    }

    return {
        follow: follow,
        unfollow: unfollow,
        current: current,
        cached: cached
    };
})();

window.FollowList = FollowList;
