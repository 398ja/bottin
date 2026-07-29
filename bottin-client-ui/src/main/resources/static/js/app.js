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
        localStorage.removeItem(this.relaysKey(userId));
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

    relaysKey: function(userId) { return 'imani.relays.' + userId; },

    loadRelays: function(userId) {
        var data = localStorage.getItem(this.relaysKey(userId));
        return data ? JSON.parse(data) : [];
    },

    saveRelays: function(userId, relays) {
        localStorage.setItem(this.relaysKey(userId), JSON.stringify(relays));
    },

    // Seeds the relay list from the server-configured defaults whenever the stored
    // list has no write relay, so an identity with no NIP-65 list still publishes.
    // Stored entries are kept; a default is added only when its URL is not listed.
    ensureRelaysSeeded: function(userId) {
        var existing = this.loadRelays(userId);
        if (existing.some(function(r) { return r.write; })) return Promise.resolve(existing);
        var self = this;
        return fetch('/api/v1/relays/defaults', { credentials: 'same-origin' })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(data) {
                var missing = ((data && data.relays) || []).filter(function(d) {
                    return !existing.some(function(r) { return r.url === d.url; });
                });
                if (!missing.length) return existing;
                var merged = existing.concat(missing);
                self.saveRelays(userId, merged);
                return merged;
            });
    },

    SESSION_TTL_MS: 15 * 60 * 1000,

    sessionKey: function(userId) { return 'imani.session.' + userId; },

    setSessionKey: function(userId, hexKey) {
        var entry = { key: hexKey, expiresAt: Date.now() + this.SESSION_TTL_MS };
        sessionStorage.setItem(this.sessionKey(userId), JSON.stringify(entry));
    },

    // Returns the live decrypted key, refreshing its idle expiry, or null once the
    // idle window has passed.
    getSessionKey: function(userId) {
        var raw = sessionStorage.getItem(this.sessionKey(userId));
        if (!raw) return null;
        var entry = JSON.parse(raw);
        if (!entry.expiresAt || entry.expiresAt <= Date.now()) {
            this.lockSession(userId);
            return null;
        }
        this.setSessionKey(userId, entry.key);
        return entry.key;
    },

    lockSession: function(userId) {
        sessionStorage.removeItem(this.sessionKey(userId));
    },

    // Verifies the passphrase, decrypts the private key, and caches it for the
    // session. Rejects on a wrong passphrase without caching anything.
    unlockSession: function(userId, passphrase) {
        var self = this;
        var identity = this.loadIdentity(userId);
        if (!identity) return Promise.reject(new Error('No identity found'));
        return NostrCrypto.verifyPassword(identity.passwordHash, identity.passwordSalt, passphrase)
            .then(function(valid) {
                if (!valid) throw new Error('Wrong passphrase');
                return NostrCrypto.decryptPrivateKey(
                    identity.privateKeyEncrypted, identity.privateKeyIv, identity.privateKeySalt, passphrase);
            })
            .then(function(hexKey) {
                self.setSessionKey(userId, hexKey);
                return hexKey;
            });
    },

    // Resolves the session key, prompting once via the shared unlock modal when the
    // session is locked or expired.
    ensureUnlocked: function(userId) {
        var live = this.getSessionKey(userId);
        if (live) return Promise.resolve(live);
        return this.promptUnlock(userId);
    },

    promptUnlock: function(userId) {
        var self = this;
        var modal = document.getElementById('unlock-modal');
        var input = document.getElementById('unlock-password');
        var error = document.getElementById('unlock-error');
        var confirmBtn = document.getElementById('unlock-confirm');
        var cancelBtn = document.getElementById('unlock-cancel');
        input.value = '';
        error.className = 'form-error hidden mt-1';
        modal.className = 'modal-overlay';
        input.focus();

        return new Promise(function(resolve, reject) {
            function cleanup() {
                modal.className = 'modal-overlay hidden';
                confirmBtn.onclick = null;
                cancelBtn.onclick = null;
            }
            confirmBtn.onclick = function() {
                self.unlockSession(userId, input.value)
                    .then(function(hexKey) { cleanup(); resolve(hexKey); })
                    .catch(function() {
                        error.textContent = 'Wrong passphrase';
                        error.className = 'form-error mt-1';
                    });
            };
            cancelBtn.onclick = function() {
                cleanup();
                // Tagged so callers can tell a deliberate cancellation from a failure.
                var cancellation = new Error('cancelled');
                cancellation.cancelled = true;
                reject(cancellation);
            };
        });
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

    // Ends the NAP session server-side, erases every trace of the key from this
    // browser (the encrypted identity in localStorage, the unlocked key and any
    // half-finished onboarding nsec in sessionStorage), then performs a real
    // navigation to the entry page: the stored identity is gone, so the unlock
    // screen would have nothing to unlock. Signing in again requires the nsec, so
    // the confirmation warns before anything is removed.
    logout: function() {
        if (!window.confirm('This logs you out and erases your key from this browser. You will need your nsec to sign in again.')) return;
        var userId = this.getIdentityUserId();
        if (userId) this.clearAll(userId);
        sessionStorage.clear();
        var goToEntry = function() { window.location.href = '/onboarding'; };
        fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'same-origin' })
            .then(goToEntry)
            .catch(goToEntry);
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
