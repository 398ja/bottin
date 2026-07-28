(function (global) {
    function message(err) {
        return err && err.message ? err.message : String(err);
    }

    function usernameOf(nip05) {
        return nip05 && nip05.indexOf('@') > 0 ? nip05.split('@')[0] : null;
    }

    // Claims the handle in the bottin directory. The pubkey is not sent: the
    // server reads it from the NAP session established moments earlier, so a
    // handle can only ever be claimed for the key that signed in.
    // ponytail: a re-run reports the handle as taken even when this same pubkey
    // already owns it; add a by-nip05 lookup if that turns out to confuse users.
    function registerHandle(username, relayUrls) {
        return fetch('/api/v1/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ username: username, relays: relayUrls })
        }).then(function (response) {
            if (response.ok) return true;
            return response.json()
                .catch(function () { return {}; })
                .then(function (body) {
                    throw new Error(body.code || ('HTTP ' + response.status));
                });
        });
    }

    function publishProfile(identity, hexKey, relayUrls) {
        var unsigned = global.NostrPublish.buildProfileEvent({
            display_name: identity.displayName,
            about: identity.about,
            picture: identity.picture,
            banner: identity.banner,
            nip05: identity.nip05,
            lud16: identity.lud16,
            website: identity.website
        });
        var signed = global.NostrCrypto.signEvent(unsigned, hexKey);
        return global.NostrPublish.publish(new global.NostrTools.SimplePool(), relayUrls, signed);
    }

    // Finishes onboarding with the key that was just minted: signs in over NAP,
    // registers the chosen handle so the user shows up in the bottin directory
    // and admin UI, and publishes the kind-0 profile to their write relays.
    // Registration and publishing are independent — one failing still reports
    // the other — and the outcome of each is returned rather than thrown.
    function run(nsec) {
        var app = global.APP;
        var userId = app.getIdentityUserId();
        var identity = userId ? app.loadIdentity(userId) : null;
        if (!identity) {
            return Promise.reject(new Error('No stored identity to complete onboarding with'));
        }

        var username = usernameOf(identity.nip05);
        var hexKey = global.NostrCrypto.nsecToHex(nsec);
        var summary = {
            registered: false,
            registerError: null,
            published: 0,
            relayCount: 0,
            publishError: null
        };

        return app.napLogin(hexKey, identity.npub)
            .then(function () { return app.ensureRelaysSeeded(userId); })
            .then(function (relays) {
                var relayUrls = (relays || [])
                    .filter(function (relay) { return relay.write; })
                    .map(function (relay) { return relay.url; });
                summary.relayCount = relayUrls.length;

                var registration = username
                    ? registerHandle(username, relayUrls)
                        .then(function () { summary.registered = true; })
                        .catch(function (err) { summary.registerError = message(err); })
                    : Promise.resolve();

                return registration.then(function () {
                    if (!relayUrls.length) {
                        summary.publishError = 'no write relay configured';
                        return;
                    }
                    return publishProfile(identity, hexKey, relayUrls)
                        .then(function (results) {
                            summary.published = results.filter(function (r) { return r.accepted; }).length;
                        })
                        .catch(function (err) { summary.publishError = message(err); });
                });
            })
            .then(function () { return summary; });
    }

    var api = { run: run };

    global.OnboardingComplete = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
