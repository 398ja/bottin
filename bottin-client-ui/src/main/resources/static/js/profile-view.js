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

    var viewedEl = el('profile-pubkey');
    var viewedPubkey = viewedEl ? viewedEl.textContent.trim() : '';

    if (!viewedPubkey || (identity && viewedPubkey === identity.pubkeyHex)) {
        // Own profile, and only the signed-in user has one to show.
        if (identity) render(identity, 'Your profile');
        return;
    }

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
