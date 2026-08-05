// The two review pages: who the signed-in user follows, and who they block.
//
// Both read their list from relays rather than from the render cache, because this
// is where a user comes to find out what is actually published. For the block list
// that also makes an otherwise write-only list recoverable: entries are encrypted,
// so this page is the only way to read back what was blocked.
//
// Names are resolved in one batched kind-0 query for every key on the list rather
// than one query per row. A key whose owner has published nothing renders as an
// abbreviated key, which is a limitation of their data rather than a failure here.
var SettingsLists = (function () {
    var METADATA_KIND = 0;
    var QUERY_MAX_WAIT_MS = 4000;

    function abbreviate(pubkeyHex) {
        return pubkeyHex.slice(0, 8) + '…' + pubkeyHex.slice(-8);
    }

    function el(id) { return document.getElementById(id); }

    // Resolves display names for many keys at once. Never rejects: a name is a nicety
    // and an unreachable relay must not stop the list itself from rendering.
    function resolveNames(userId, pubkeys) {
        if (!pubkeys.length) return Promise.resolve({});

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
                var newest = {};
                (events || []).forEach(function (event) {
                    var held = newest[event.pubkey];
                    if (!held || event.created_at > held.created_at) newest[event.pubkey] = event;
                });
                var names = {};
                Object.keys(newest).forEach(function (pubkey) {
                    try {
                        var metadata = JSON.parse(newest[pubkey].content || '{}');
                        var name = metadata.display_name || metadata.name;
                        if (name) names[pubkey] = name;
                    } catch (ignored) { /* a profile we cannot parse has no name to show */ }
                });
                release();
                return names;
            }, function (err) {
                release();
                throw err;
            });
        }).catch(function () { return {}; });
    }

    function row(pubkey, name, options, undo) {
        var undoLabel = options.undoLabel;
        var element = document.createElement('div');
        element.className = 'search-result';

        var link = document.createElement('a');
        link.className = 'search-result-link';
        link.href = '/profile/' + encodeURIComponent(pubkey);

        var info = document.createElement('div');
        info.className = 'search-result-info';

        var title = document.createElement('div');
        title.className = 'search-result-name';
        title.textContent = name || abbreviate(pubkey);
        info.appendChild(title);

        // The key is always on show, even when a name is: a name is self-asserted
        // and two people may publish the same one.
        var detail = document.createElement('div');
        detail.className = 'search-result-detail form-input-mono';
        detail.textContent = abbreviate(pubkey);
        info.appendChild(detail);

        link.appendChild(info);
        element.appendChild(link);

        var button = document.createElement('button');
        button.className = 'btn btn-sm btn-outline';
        button.textContent = undoLabel;
        button.addEventListener('click', function () {
            button.disabled = true;
            undo(pubkey)
                .then(function (result) {
                    if (result.published > 0 || result.unchanged) {
                        element.remove();
                    } else {
                        window.ListFeedback.reportOutcome(result, undoLabel);
                    }
                })
                .catch(function (err) {
                    window.ListFeedback.reportRefusal(err, options.listName, options.verb);
                })
                .then(function () { button.disabled = false; });
        });
        element.appendChild(button);

        return element;
    }

    function message(container, icon, text) {
        container.innerHTML = '';
        var empty = document.createElement('div');
        empty.className = 'empty-state';
        var glyph = document.createElement('div');
        glyph.className = 'empty-state-icon';
        glyph.textContent = icon;
        var paragraph = document.createElement('p');
        paragraph.textContent = text;
        empty.appendChild(glyph);
        empty.appendChild(paragraph);
        container.appendChild(empty);
    }

    // `emptyText` is only shown for a list the relays confirmed is empty. A list that
    // could not be read says so instead: telling someone they follow nobody when the
    // question was never answered is a different and possibly false statement.
    function render(userId, options) {
        var container = el(options.containerId);
        if (!container) return Promise.resolve();

        return options.load(userId).then(function (found) {
            if (!found.readable) {
                message(container, '⚠️', options.unreadableText);
                return;
            }
            if (!found.pubkeys.length) {
                message(container, options.emptyIcon, options.emptyText);
                return;
            }
            return resolveNames(userId, found.pubkeys).then(function (names) {
                container.innerHTML = '';
                found.pubkeys.forEach(function (pubkey) {
                    container.appendChild(row(pubkey, names[pubkey], options, function (key) {
                        return options.undo(userId, key);
                    }));
                });
            });
        }).catch(function () {
            message(container, '⚠️', options.unreadableText);
        });
    }

    function initFollows() {
        var userId = window.APP.getIdentityUserId();
        if (!userId || !el('follows-list')) return Promise.resolve();
        return render(userId, {
            containerId: 'follows-list',
            load: function (user) { return window.FollowList.current(user); },
            undo: function (user, key) { return window.FollowList.unfollow(user, key); },
            undoLabel: 'Unfollow',
            listName: 'follow',
            verb: 'unfollowing',
            emptyIcon: '👥',
            emptyText: 'You are not following anyone yet',
            unreadableText: 'Your follow list could not be read'
        });
    }

    function initBlocks() {
        var userId = window.APP.getIdentityUserId();
        if (!userId || !el('blocks-list')) return Promise.resolve();
        return render(userId, {
            containerId: 'blocks-list',
            load: function (user) { return window.BlockList.current(user); },
            undo: function (user, key) { return window.BlockList.unblock(user, key); },
            undoLabel: 'Unblock',
            listName: 'block',
            verb: 'unblocking',
            emptyIcon: '🚫',
            emptyText: 'No blocked users',
            unreadableText: 'Your block list could not be read'
        });
    }

    return { initFollows: initFollows, initBlocks: initBlocks };
})();

window.SettingsLists = SettingsLists;

document.addEventListener('DOMContentLoaded', function () {
    SettingsLists.initFollows();
    SettingsLists.initBlocks();
});
