document.addEventListener('DOMContentLoaded', function () {
    var nameEl = document.getElementById('profile-name');
    if (!nameEl) return;

    var userId = APP.getIdentityUserId();
    var identity = userId ? APP.loadIdentity(userId) : null;

    function el(id) { return document.getElementById(id); }

    function show(element) { element.classList.remove('hidden'); }

    function fill(textElementId, rowElementId, value) {
        if (!value) return;
        el(textElementId).textContent = value;
        show(el(rowElementId));
    }

    function render(profile, fallbackName) {
        nameEl.textContent = profile.displayName || profile.nip05 || fallbackName;
        el('profile-nip05').textContent = profile.nip05 || '';
        fill('profile-about', 'profile-about', profile.about);
        fill('profile-lud16', 'profile-lud16-row', profile.lud16);

        // safeImageUrl is a generic http(s) guard; the website link needs the same one.
        var website = APP.safeImageUrl(profile.website);
        if (website) {
            var link = el('profile-website');
            link.textContent = website;
            link.href = website;
            show(el('profile-website-row'));
        }

        var avatar = el('profile-avatar');
        avatar.onerror = function () { this.src = '/img/default-avatar.svg'; };
        var pictureUrl = APP.safeImageUrl(profile.picture);
        if (pictureUrl) avatar.src = pictureUrl;

        var bannerUrl = APP.safeImageUrl(profile.banner);
        if (bannerUrl) {
            var banner = el('profile-banner-image');
            banner.onerror = function () { this.classList.add('hidden'); };
            banner.src = bannerUrl;
            show(banner);
        }
    }

    function abbreviate(pubkeyHex) {
        return pubkeyHex.slice(0, 8) + '…' + pubkeyHex.slice(-8);
    }

    // Offers the follow control for another key's profile. Nothing is rendered for a
    // reader who is not signed in: they hold no key, so there is nothing to follow
    // with, and a control that fails when pressed is worse than none.
    // Offers the follow and block controls for another key's profile. Nothing is
    // rendered for a reader who is not signed in: they hold no key, so there is
    // nothing to follow or block with, and a control that fails when pressed is
    // worse than none.
    //
    // A blocked key's profile stays reachable and readable. Hiding it would leave
    // no way to undo the block from the page that caused it.
    function renderActions(pubkey) {
        var actions = el('profile-actions');
        if (!actions || !userId) return;

        actions.classList.remove('hidden');
        actions.innerHTML = '';

        if (BlockList.cached(userId).indexOf(pubkey) !== -1) {
            var badge = document.createElement('span');
            badge.className = 'badge badge-danger';
            badge.textContent = 'Blocked';
            actions.appendChild(badge);
            actions.appendChild(blockControl(userId, pubkey, true));
            return;
        }

        actions.appendChild(followControl(userId, pubkey));
        actions.appendChild(blockControl(userId, pubkey, false));
    }

    function blockControl(user, pubkey, blocked) {
        var button = document.createElement('button');
        button.className = 'btn btn-outline';
        button.textContent = blocked ? 'Unblock' : 'Block';

        button.addEventListener('click', function () {
            button.disabled = true;
            var action = blocked ? BlockList.unblock : BlockList.block;
            var doneLabel = blocked ? 'Unblocked' : 'Blocked';
            action(user, pubkey)
                .then(function (result) {
                    if (result.published === 0 && !result.unchanged) {
                        APP.showToast('Publish failed on all relays', 'error');
                        return;
                    }
                    if (!result.unchanged) {
                        APP.showToast(result.published < result.of
                            ? doneLabel + ' · published to ' + result.published + ' of ' + result.of + ' relays'
                            : doneLabel, 'success');
                    }
                    renderActions(pubkey);
                })
                .catch(function (err) {
                    if (err && err.code === 'unreadable') {
                        APP.showToast('Could not read your block list. Not publishing.', 'error');
                    } else if (err && err.code === 'no_write_relays') {
                        APP.showToast('Add at least one write relay before blocking.', 'error');
                    }
                })
                .then(function () { button.disabled = false; });
        });

        return button;
    }

    function followControl(user, pubkey) {
        var button = document.createElement('button');
        var following = FollowList.cached(user).indexOf(pubkey) !== -1;

        function paint() {
            button.textContent = following ? 'Following' : 'Follow';
            button.className = 'btn ' + (following ? 'btn-outline' : 'btn-primary');
        }
        paint();

        button.addEventListener('click', function () {
            button.disabled = true;
            var action = following ? FollowList.unfollow : FollowList.follow;
            var doneLabel = following ? 'Unfollowed' : 'Following';
            action(user, pubkey)
                .then(function (result) {
                    if (result.published > 0 || result.unchanged) following = !following;
                    if (result.unchanged) return;
                    if (result.published === 0) {
                        APP.showToast('Publish failed on all relays', 'error');
                    } else if (result.published < result.of) {
                        APP.showToast(doneLabel + ' · published to ' + result.published
                                + ' of ' + result.of + ' relays', 'success');
                    } else {
                        APP.showToast(doneLabel, 'success');
                    }
                })
                .catch(function (err) {
                    if (err && err.code === 'unreadable') {
                        APP.showToast('Could not read your follow list. Not publishing.', 'error');
                    } else if (err && err.code === 'no_write_relays') {
                        APP.showToast('Add at least one write relay before following.', 'error');
                    }
                })
                .then(function () { button.disabled = false; paint(); });
        });

        return button;
    }

    var viewedEl = el('profile-pubkey');
    var viewedPubkey = viewedEl ? viewedEl.textContent.trim() : '';

    if (!viewedPubkey || (identity && viewedPubkey === identity.pubkeyHex)) {
        // Own profile, and only the signed-in user has one to show.
        if (identity) render(identity, 'Your profile');
        return;
    }

    renderActions(viewedPubkey);

    // This browser holds nothing about anyone but its own user, so another key's
    // profile is read from relays and is never saved: the stored identity belongs
    // to the signed-in user alone. An unreachable relay or an unpublished profile
    // leaves the key itself on show, which is still the truthful answer to "who
    // is this".
    //
    // A reader who is not signed in has no relays of their own, and resolves to the
    // deployment's — which is why the endpoint serving those carries no session
    // guard while the rest of the relay API does.
    APP.effectiveReadRelays(userId)
        .then(function (readRelays) {
            var pool = new NostrTools.SimplePool();
            return ProfileFetch.fetch(pool, readRelays, viewedPubkey).then(function (profile) {
                try { pool.close(readRelays); } catch (ignored) { /* pool already closed */ }
                return profile;
            });
        })
        .catch(function () { return null; })
        .then(function (profile) { render(profile || {}, abbreviate(viewedPubkey)); });
});
