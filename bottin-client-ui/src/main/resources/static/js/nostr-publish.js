(function (global) {
    function nowSeconds() {
        return Math.floor(Date.now() / 1000);
    }

    // Builds a kind-0 metadata event. Empty fields are omitted; `name` is the
    // local part of the nip05 identifier so clients that key off `name` resolve it.
    function buildProfileEvent(fields) {
        var f = fields || {};
        var content = {};
        if (f.nip05 && f.nip05.indexOf('@') > 0) {
            content.name = f.nip05.split('@')[0];
        }
        var keys = ['display_name', 'about', 'picture', 'banner', 'nip05', 'lud16', 'website'];
        keys.forEach(function (k) {
            if (f[k]) content[k] = f[k];
        });
        return { kind: 0, created_at: nowSeconds(), tags: [], content: JSON.stringify(content) };
    }

    // NIP-65 r tags: both read+write -> ["r", url]; otherwise the single marker.
    // A relay with neither read nor write is dropped.
    function relaysToTags(relays) {
        var tags = [];
        (relays || []).forEach(function (r) {
            if (r.read && r.write) {
                tags.push(['r', r.url]);
            } else if (r.read) {
                tags.push(['r', r.url, 'read']);
            } else if (r.write) {
                tags.push(['r', r.url, 'write']);
            }
        });
        return tags;
    }

    function buildRelayListEvent(relays) {
        return { kind: 10002, created_at: nowSeconds(), tags: relaysToTags(relays), content: '' };
    }

    // Builds any replaceable list event: kind 3 for follows, kind 10000 for blocks.
    // Takes tags and content already assembled by the caller rather than assembling
    // them, because both carry entries this application did not author and must not
    // rebuild - see ReplaceableList and its codecs.
    function buildReplaceableListEvent(kind, tags, content) {
        return { kind: kind, created_at: nowSeconds(), tags: tags || [], content: content || '' };
    }

    // Broadcasts a signed event to the given relays via a SimplePool-shaped pool and
    // resolves per-relay accepted/reason results, never rejecting.
    function publish(pool, relayUrls, signedEvent) {
        var promises = pool.publish(relayUrls, signedEvent);
        return Promise.allSettled(promises).then(function (settled) {
            return settled.map(function (res, i) {
                return {
                    url: relayUrls[i],
                    accepted: res.status === 'fulfilled',
                    reason: res.status === 'fulfilled' ? null : String(res.reason)
                };
            });
        });
    }

    var api = {
        buildProfileEvent: buildProfileEvent,
        buildRelayListEvent: buildRelayListEvent,
        buildReplaceableListEvent: buildReplaceableListEvent,
        relaysToTags: relaysToTags,
        publish: publish
    };

    global.NostrPublish = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
