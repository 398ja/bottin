// Published profiles (NIP-01 kind 0) for many keys in one relay query.
//
// The pages that show people - search results, the follow and block lists - all hold
// a list of keys before they hold anything to show for them. The directory's own
// records carry a handle and a key but no picture: that lives in the owner's kind-0
// on the relays. One batched query per page beats one per row.
//
// Signed-out visitors resolve too. `effectiveReadRelays` falls back to the
// deployment's system relays when nobody is signed in, so a search still shows faces.
var ProfileLookup = (function () {
    var METADATA_KIND = 0;
    var QUERY_MAX_WAIT_MS = 4000;
    var DEFAULT_AVATAR = '/img/default-avatar.svg';

    function newestPerAuthor(events) {
        var newest = {};
        (events || []).forEach(function (event) {
            var held = newest[event.pubkey];
            if (!held || event.created_at > held.created_at) newest[event.pubkey] = event;
        });
        return newest;
    }

    function parseProfiles(newest) {
        var profiles = {};
        Object.keys(newest).forEach(function (pubkey) {
            try {
                var metadata = JSON.parse(newest[pubkey].content || '{}');
                profiles[pubkey] = {
                    name: metadata.display_name || metadata.name,
                    picture: metadata.picture,
                    nip05: metadata.nip05
                };
            } catch (ignored) { /* a profile we cannot parse has nothing to show */ }
        });
        return profiles;
    }

    // Resolves {pubkey: {name, picture, nip05}}, omitting whoever published nothing.
    // Never rejects: a profile is a nicety and an unreachable relay must not stop the
    // page that asked for it from rendering. It resolves {} instead.
    function resolve(userId, pubkeys) {
        if (!pubkeys || !pubkeys.length) return Promise.resolve({});

        return window.APP.effectiveReadRelays(userId).then(function (readRelays) {
            if (!readRelays || !readRelays.length) return {};
            var pool = new window.NostrTools.SimplePool();

            // Released on both paths. Closing only on success leaks the sockets in
            // exactly the case the outer catch exists for - an unreachable relay.
            function release() {
                try { pool.close(readRelays); } catch (ignored) { /* already closed */ }
            }

            return pool.querySync(
                readRelays,
                { kinds: [METADATA_KIND], authors: pubkeys },
                { maxWait: QUERY_MAX_WAIT_MS }
            ).then(function (events) {
                release();
                return parseProfiles(newestPerAuthor(events));
            }, function (err) {
                release();
                throw err;
            });
        }).catch(function () { return {}; });
    }

    return { resolve: resolve, DEFAULT_AVATAR: DEFAULT_AVATAR };
})();

window.ProfileLookup = ProfileLookup;
