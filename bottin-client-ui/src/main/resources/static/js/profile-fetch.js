(function (global) {
    var METADATA_KIND = 0;
    var QUERY_MAX_WAIT_MS = 4000;

    // Fields carried on a stored identity, paired with the kind-0 keys they come
    // from. `name` is the NIP-01 fallback for a missing display_name.
    var FIELDS = [
        { identity: 'displayName', keys: ['display_name', 'name'] },
        { identity: 'about', keys: ['about'] },
        { identity: 'picture', keys: ['picture'] },
        { identity: 'banner', keys: ['banner'] },
        { identity: 'nip05', keys: ['nip05'] },
        { identity: 'lud16', keys: ['lud16'] },
        { identity: 'website', keys: ['website'] }
    ];

    function firstValue(metadata, keys) {
        for (var i = 0; i < keys.length; i++) {
            var value = metadata[keys[i]];
            if (value) return value;
        }
        return null;
    }

    function toProfile(metadata) {
        var profile = {};
        FIELDS.forEach(function (field) {
            profile[field.identity] = firstValue(metadata, field.keys);
        });
        return profile;
    }

    // Reads the published profile for a pubkey. Relays disagree and retain
    // different history, so the newest kind-0 across all of them wins rather than
    // whichever answers first. Returns null when nothing is published or the
    // lookup fails: a sign-in must not hinge on a relay being reachable.
    function fetch(pool, relayUrls, pubkeyHex) {
        if (!relayUrls || !relayUrls.length) return Promise.resolve(null);
        return pool.querySync(
            relayUrls,
            { kinds: [METADATA_KIND], authors: [pubkeyHex] },
            { maxWait: QUERY_MAX_WAIT_MS }
        ).then(function (events) {
            if (!events || !events.length) return null;
            var newest = events.slice().sort(function (a, b) { return b.created_at - a.created_at; })[0];
            return toProfile(JSON.parse(newest.content || '{}'));
        }).catch(function () {
            return null;
        });
    }

    // Copies published values onto the identity, leaving locally held ones in
    // place where the profile says nothing: an absent field on a relay is silence,
    // not an instruction to erase what this browser already knows.
    function applyTo(identity, profile) {
        if (!profile) return identity;
        FIELDS.forEach(function (field) {
            if (profile[field.identity]) identity[field.identity] = profile[field.identity];
        });
        return identity;
    }

    // Refreshes a stored identity from its own relays and saves it. Resolves with
    // the identity either way, so callers can render without branching on whether
    // the relays answered.
    function refresh(userId) {
        var app = global.APP;
        var identity = app.loadIdentity(userId);
        if (!identity || !identity.pubkeyHex) return Promise.resolve(identity);

        return app.ensureRelaysSeeded(userId).then(function (relays) {
            var readRelays = (relays || [])
                .filter(function (relay) { return relay.read; })
                .map(function (relay) { return relay.url; });
            var pool = new global.NostrTools.SimplePool();
            return fetch(pool, readRelays, identity.pubkeyHex)
                .then(function (profile) {
                    try { pool.close(readRelays); } catch (ignored) { /* pool already closed */ }
                    if (!profile) return identity;
                    app.saveIdentity(applyTo(identity, profile));
                    return identity;
                });
        }).catch(function () {
            return identity;
        });
    }

    var api = { fetch: fetch, applyTo: applyTo, refresh: refresh };

    global.ProfileFetch = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
