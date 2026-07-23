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
    }
};
}

if (!window.__appInitialized) {
window.__appInitialized = true;
document.addEventListener('DOMContentLoaded', function() {
    var authedPages = ['/search', '/profile', '/settings'];
    var currentPath = window.location.pathname;
    var isAuthedPage = authedPages.some(function(p) { return currentPath.startsWith(p); });
    if (isAuthedPage) {
        APP.checkSession();
    }
});
}
