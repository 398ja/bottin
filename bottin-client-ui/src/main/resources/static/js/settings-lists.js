// The two review pages: who the signed-in user follows, and who they block.
//
// Both read their list from relays rather than from the render cache, because this
// is where a user comes to find out what is actually published. For the block list
// that also makes an otherwise write-only list recoverable: entries are encrypted,
// so this page is the only way to read back what was blocked.
//
// Profiles come from ProfileLookup, one batched kind-0 query for every key on the
// list rather than one query per row. A key whose owner has published nothing renders
// as an abbreviated key, which is a limitation of their data rather than a failure here.
var SettingsLists = (function () {
    function abbreviate(pubkeyHex) {
        return pubkeyHex.slice(0, 8) + '…' + pubkeyHex.slice(-8);
    }

    function el(id) { return document.getElementById(id); }

    function row(pubkey, profile, options, undo) {
        var found = profile || {};
        var undoLabel = options.undoLabel;
        var element = document.createElement('div');
        element.className = 'search-result';

        var link = document.createElement('a');
        link.className = 'search-result-link';
        link.href = '/profile/' + encodeURIComponent(pubkey);

        var avatar = document.createElement('img');
        avatar.alt = '';
        avatar.className = 'avatar-sm';
        // A picture URL that will not load is as good as none: the placeholder keeps
        // the row's shape rather than leaving a broken image beside the name.
        avatar.onerror = function () { this.src = window.ProfileLookup.DEFAULT_AVATAR; };
        avatar.src = found.picture || window.ProfileLookup.DEFAULT_AVATAR;
        link.appendChild(avatar);

        var info = document.createElement('div');
        info.className = 'search-result-info';

        var title = document.createElement('div');
        title.className = 'search-result-name';
        title.textContent = found.name || abbreviate(pubkey);
        info.appendChild(title);

        // The NIP-05 identifier is what distinguishes two people publishing the same
        // display name. Whoever has published none falls back to their key, which is
        // the only identifier they have given us.
        var detail = document.createElement('div');
        detail.className = 'search-result-detail';
        if (found.nip05) {
            detail.textContent = found.nip05;
        } else {
            detail.className += ' form-input-mono';
            detail.textContent = abbreviate(pubkey);
        }
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
            return window.ProfileLookup.resolve(userId, found.pubkeys).then(function (profiles) {
                container.innerHTML = '';
                found.pubkeys.forEach(function (pubkey) {
                    container.appendChild(row(pubkey, profiles[pubkey], options, function (key) {
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
