(function (global) {
    // Onboarding renders through the same layout as every other page, and an
    // identity exists by the time the backup-key screen loads. Suppressing the
    // banner there keeps it from competing with the one screen a user must not
    // skim past.
    var ONBOARDING_PATH = '/onboarding';

    function isOnboarding(pathname) {
        return pathname.indexOf(ONBOARDING_PATH) === 0;
    }

    function hasPublishedName(identity) {
        return !!(identity && identity.displayName);
    }

    function isDismissed(userId) {
        return !!localStorage.getItem(global.APP.nudgeKey(userId));
    }

    /**
     * Whether a user who has not filled their profile in should be offered the
     * route to it: they are signed in, have published no display name, have not
     * dismissed the offer, and are not still registering.
     */
    function shouldShow(userId, pathname) {
        if (!userId || isOnboarding(pathname)) {
            return false;
        }
        if (isDismissed(userId)) {
            return false;
        }
        return !hasPublishedName(global.APP.loadIdentity(userId));
    }

    function dismiss(userId) {
        localStorage.setItem(global.APP.nudgeKey(userId), '1');
    }

    /**
     * Shows or hides the banner for the current page. Exposed rather than bound
     * to load alone so the decision can be exercised without driving a browser.
     */
    function apply(pathname) {
        var banner = document.getElementById('profile-nudge');
        if (!banner) {
            return;
        }
        var userId = global.APP.getIdentityUserId();
        if (!shouldShow(userId, pathname)) {
            banner.classList.add('hidden');
            return;
        }
        banner.classList.remove('hidden');

        var dismissButton = document.getElementById('profile-nudge-dismiss');
        if (dismissButton) {
            dismissButton.addEventListener('click', function () {
                dismiss(userId);
                banner.classList.add('hidden');
            });
        }
    }

    var api = { apply: apply, shouldShow: shouldShow, dismiss: dismiss };

    global.ProfileNudge = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }

    if (typeof document !== 'undefined') {
        document.addEventListener('DOMContentLoaded', function () {
            apply(global.location.pathname);
        });
    }
})(typeof window !== 'undefined' ? window : globalThis);
