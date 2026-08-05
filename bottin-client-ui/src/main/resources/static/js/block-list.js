// Who the signed-in user blocks, as a NIP-51 mute list.
//
// Spec: https://github.com/nostr-protocol/nips/blob/master/51.md
//       https://github.com/nostr-protocol/nips/blob/master/44.md
//
// Blocked keys live in the encrypted `content`, sealed with NIP-44 to the user's own
// key. Nothing about whom a user blocks is published in the clear: a public blocklist
// is a durable, unretractable statement about another person, and this deployment ties
// keys to real registered handles. The public `tags` array is carried across from the
// previous event and never receives a `p` entry from here.
//
// The decrypted payload is a tag array of the same shape as `tags`, and may hold muted
// hashtags, words or threads that this application does not offer. Those pass through
// untouched: the document is shared with other clients.
var BlockList = (function () {
    var KIND = 10000;
    var PUBKEY_PATTERN = /^[0-9a-f]{64}$/;

    function hexToBytes(hex) {
        var bytes = new Uint8Array(hex.length / 2);
        for (var i = 0; i < bytes.length; i++) {
            bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
        }
        return bytes;
    }

    function sealingKey(hexKey, ownPubkey) {
        return window.NostrTools.nip44.getConversationKey(hexToBytes(hexKey), ownPubkey);
    }

    function blockedKeys(tags) {
        return tags
            .filter(function (tag) { return tag[0] === 'p' && tag[1]; })
            .map(function (tag) { return tag[1]; });
    }

    var spec = {
        kind: KIND,

        // A list that will not decrypt is unreadable in the sense that matters:
        // replacing it would discard every key the user has blocked. That is the same
        // failure as a relay that never answered, and is refused the same way.
        decode: function (event, hexKey, ownPubkey) {
            if (!event || !event.content) return [];
            try {
                return JSON.parse(window.NostrTools.nip44.decrypt(
                    event.content, sealingKey(hexKey, ownPubkey)));
            } catch (e) {
                throw ReplaceableList.error('unreadable',
                    'The block list could not be deciphered');
            }
        },

        encode: function (previous, tags, hexKey, ownPubkey) {
            var content = window.NostrTools.nip44.encrypt(
                JSON.stringify(tags), sealingKey(hexKey, ownPubkey));
            return window.NostrPublish.buildReplaceableListEvent(
                KIND, previous ? previous.tags : [], content);
        }
    };

    function requirePubkey(pubkey) {
        if (!PUBKEY_PATTERN.test(pubkey || '')) {
            throw ReplaceableList.error('invalid_pubkey', '"' + pubkey + '" is not a Nostr public key');
        }
    }

    function cacheOnConfirmed(userId, result) {
        if (result.published > 0) {
            window.APP.saveBlockList(userId, blockedKeys(result.entries));
        }
        return result;
    }

    function block(userId, pubkey, injectedPool) {
        try {
            requirePubkey(pubkey);
        } catch (e) {
            return Promise.reject(e);
        }
        return ReplaceableList.mutate(userId, spec, function (tags) {
            if (blockedKeys(tags).indexOf(pubkey) !== -1) return null;
            return tags.concat([['p', pubkey]]);
        }, injectedPool).then(function (result) { return cacheOnConfirmed(userId, result); });
    }

    function unblock(userId, pubkey, injectedPool) {
        try {
            requirePubkey(pubkey);
        } catch (e) {
            return Promise.reject(e);
        }
        return ReplaceableList.mutate(userId, spec, function (tags) {
            if (blockedKeys(tags).indexOf(pubkey) === -1) return null;
            return tags.filter(function (tag) { return !(tag[0] === 'p' && tag[1] === pubkey); });
        }, injectedPool).then(function (result) { return cacheOnConfirmed(userId, result); });
    }

    // Reads and deciphers the published list. Needs the key, so it prompts for the
    // passphrase if the session is locked. Reports `readable: false` for a list that
    // could not be read at all rather than showing it as "you have blocked nobody".
    function current(userId, injectedPool) {
        return ReplaceableList.read(userId, KIND, injectedPool).then(function (found) {
            if (!found.readable || !found.event) {
                return { pubkeys: [], readable: found.readable };
            }
            var identity = window.APP.loadIdentity(userId);
            return window.APP.ensureUnlocked(userId).then(function (hexKey) {
                try {
                    return {
                        pubkeys: blockedKeys(spec.decode(found.event, hexKey, identity.pubkeyHex)),
                        readable: true
                    };
                } catch (e) {
                    return { pubkeys: [], readable: false };
                }
            });
        });
    }

    function cached(userId) {
        return window.APP.loadBlockList(userId) || [];
    }

    return {
        block: block,
        unblock: unblock,
        current: current,
        cached: cached
    };
})();

window.BlockList = BlockList;
