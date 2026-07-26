if (window.APP) { /* already loaded */ } else {
window.APP = {
    identityKey: function(userId) { return 'imani.identity.' + userId; },
    followsKey: function(userId) { return 'imani.follows.' + userId; },
    blocksKey: function(userId) { return 'imani.blocks.' + userId; },

    debounce: function(fn, delay) {
        let timer;
        return function() {
            const context = this;
            const args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function() { fn.apply(context, args); }, delay);
        };
    },

    // Runs the NAP challenge/sign/complete handshake with a hex private key and
    // establishes the session cookie. Resolves on success, rejects otherwise.
    napLogin: function(hexKey, npub) {
        return fetch('/api/v1/auth/init', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ npub: npub })
        })
        .then(function(r) { return r.json(); })
        .then(function(challenge) {
            return NostrCrypto.signNip98Event(
                challenge.challenge, challenge.challenge_id, challenge.auth_url, 'POST', hexKey
            ).then(function(signedEvent) {
                return fetch('/api/v1/auth/complete', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Nostr ' + signedEvent
                    },
                    body: JSON.stringify({ challenge_id: challenge.challenge_id })
                });
            });
        })
        .then(function(r) {
            if (!r.ok) throw new Error('Authentication failed');
        });
    },

    saveIdentity: function(identity) {
        localStorage.setItem(this.identityKey(identity.userId), JSON.stringify(identity));
    },

    loadIdentity: function(userId) {
        var data = localStorage.getItem(this.identityKey(userId));
        return data ? JSON.parse(data) : null;
    },

    saveFollowList: function(userId, follows) {
        localStorage.setItem(this.followsKey(userId), JSON.stringify(follows));
    },

    loadFollowList: function(userId) {
        var data = localStorage.getItem(this.followsKey(userId));
        return data ? JSON.parse(data) : [];
    },

    saveBlockList: function(userId, blocks) {
        localStorage.setItem(this.blocksKey(userId), JSON.stringify(blocks));
    },

    loadBlockList: function(userId) {
        var data = localStorage.getItem(this.blocksKey(userId));
        return data ? JSON.parse(data) : [];
    },

    clearAll: function(userId) {
        localStorage.removeItem(this.identityKey(userId));
        localStorage.removeItem(this.followsKey(userId));
        localStorage.removeItem(this.blocksKey(userId));
    },

    getIdentityUserId: function() {
        for (var i = 0; i < localStorage.length; i++) {
            var key = localStorage.key(i);
            if (key && key.startsWith('imani.identity.')) {
                return key.substring('imani.identity.'.length);
            }
        }
        return null;
    },

    showToast: function(message, type) {
        var toast = document.createElement('div');
        toast.className = 'toast toast-' + type;
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(function() { toast.remove(); }, 3000);
    },

    checkSession: function() {
        fetch('/api/v1/auth/session', { credentials: 'same-origin' })
            .then(function(r) {
                if (r.status === 401) {
                    window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
                }
            });
    },

    // Accepts only http(s) URLs and returns the normalized href; returns null for
    // anything else so profile picture URLs (which can originate from relay data)
    // cannot trigger unexpected schemes such as javascript: or data:.
    safeImageUrl: function(value) {
        try {
            var url = new URL(value);
            if (url.protocol === 'https:' || url.protocol === 'http:') return url.href;
        } catch (e) { /* invalid or non-absolute URL */ }
        return null;
    },

    // Points the nav avatar at the stored identity's picture when one is present
    // and valid, falling back to the bundled default on absence or load error.
    // The server holds no profile data, so the avatar is sourced client-side.
    populateNavAvatar: function() {
        var avatar = document.getElementById('nav-avatar');
        if (!avatar) return;
        var userId = this.getIdentityUserId();
        if (!userId) return;
        var identity = this.loadIdentity(userId);
        var pictureUrl = identity && this.safeImageUrl(identity.picture);
        if (!pictureUrl) return;
        avatar.onerror = function() { this.src = '/img/default-avatar.svg'; };
        avatar.src = pictureUrl;
    },

    // Reveals the authenticated nav (Search, avatar dropdown) once an active NAP
    // session is confirmed. Page routes are anonymous-accessible and carry no
    // server-side principal, so nav visibility is decided client-side.
    revealAuthenticatedNav: function() {
        var authedNav = document.getElementById('nav-authed');
        if (!authedNav) return;
        var self = this;
        fetch('/api/v1/auth/session', { credentials: 'same-origin' })
            .then(function(r) {
                if (r.status === 200) {
                    authedNav.classList.remove('hidden');
                    self.populateNavAvatar();
                }
            })
            .catch(function() { /* no session established: leave nav hidden */ });
    },

    // Ends the NAP session server-side, then performs a real navigation to the
    // login page. The stored identity is retained on purpose: the private key
    // is held encrypted, so the returning user can unlock with their passphrase.
    // Logout ends the session; it does not forget the device.
    logout: function() {
        if (!window.confirm('This will clear your session.')) return;
        var goToLogin = function() { window.location.href = '/login'; };
        fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'same-origin' })
            .then(goToLogin)
            .catch(goToLogin);
    }
};
}

if (!window.__appInitialized) {
window.__appInitialized = true;
document.addEventListener('DOMContentLoaded', function() {
    APP.revealAuthenticatedNav();
    var authedPages = ['/apps', '/search', '/profile', '/settings'];
    var currentPath = window.location.pathname;
    var isAuthedPage = authedPages.some(function(p) { return currentPath.startsWith(p); });
    if (isAuthedPage) {
        APP.checkSession();
    }
});
}
