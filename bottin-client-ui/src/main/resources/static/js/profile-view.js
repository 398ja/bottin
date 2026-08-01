document.addEventListener('DOMContentLoaded', function () {
    var nameEl = document.getElementById('profile-name');
    if (!nameEl) return;

    var userId = APP.getIdentityUserId();
    var identity = userId ? APP.loadIdentity(userId) : null;
    if (!identity) return;

    function el(id) { return document.getElementById(id); }

    function show(element) { element.classList.remove('hidden'); }

    function fill(textElementId, rowElementId, value) {
        if (!value) return;
        el(textElementId).textContent = value;
        show(el(rowElementId));
    }

    nameEl.textContent = identity.displayName || identity.nip05 || 'Your profile';
    el('profile-nip05').textContent = identity.nip05 || '';
    fill('profile-about', 'profile-about', identity.about);
    fill('profile-lud16', 'profile-lud16-row', identity.lud16);

    // safeImageUrl is a generic http(s) guard; the website link needs the same one.
    var website = APP.safeImageUrl(identity.website);
    if (website) {
        var link = el('profile-website');
        link.textContent = website;
        link.href = website;
        show(el('profile-website-row'));
    }

    var avatar = el('profile-avatar');
    avatar.onerror = function () { this.src = '/img/default-avatar.svg'; };
    var pictureUrl = APP.safeImageUrl(identity.picture);
    if (pictureUrl) avatar.src = pictureUrl;

    var bannerUrl = APP.safeImageUrl(identity.banner);
    if (bannerUrl) {
        var banner = el('profile-banner-image');
        banner.onerror = function () { this.classList.add('hidden'); };
        banner.src = bannerUrl;
        show(banner);
    }
});
