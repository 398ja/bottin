(function (global) {
    // The key minted for this registration attempt, kept across a retry. A
    // handle lost to someone else must not cost the user their key: the NAP
    // session already established belongs to this key, and minting a second one
    // would discard the key the backup screen is about to show them.
    var pending = null;

    function message(err) {
        return err && err.message ? err.message : String(err);
    }

    function failure(code, text) {
        var error = new Error(text);
        error.code = code;
        return error;
    }

    function describeClaimFailure(code, username) {
        if (code === 'USERNAME_TAKEN') {
            return 'Registration stopped. The handle "' + username + '" was claimed by somebody else '
                + 'while you were filling in the form. Suggestion: choose a different handle and '
                + 'submit again — your password has been kept.';
        }
        if (code === 'NAP_SESSION_REQUIRED') {
            return 'Registration stopped. The sign-in proving you control your new key did not take. '
                + 'Suggestion: reload this page and try again.';
        }
        if (code === 'INVALID_USERNAME') {
            return 'Registration stopped. The directory does not accept "' + username + '" as a handle. '
                + 'Suggestion: use lower-case letters, digits, hyphens and underscores only.';
        }
        return 'Registration stopped. The directory refused the handle "' + username + '" (' + code + '). '
            + 'Suggestion: try again in a moment, or choose a different handle.';
    }

    // Mints the key and signs in with it, once per attempt. The key exists only
    // in memory here: it is needed to authenticate the claim, because the
    // directory reads the claimant from the session rather than from the
    // request body, but existing is not the same as being stored.
    function signedInKeypair() {
        if (pending) {
            return Promise.resolve(pending);
        }
        var keypair = global.NostrCrypto.generateKeypair();
        return global.APP.napLogin(keypair.privateKeyHex, keypair.npub)
            .then(function () {
                pending = keypair;
                return keypair;
            });
    }

    // Claims the handle in the directory. Rejects unless the directory now holds
    // it, which is what stops anything downstream from storing a mapping that
    // was refused.
    function claimHandle(username, relayUrls) {
        return fetch('/api/v1/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ username: username, relays: relayUrls })
        }).then(function (response) {
            if (response.ok) {
                return true;
            }
            return response.json()
                .catch(function () { return {}; })
                .then(function (body) {
                    var code = body.code || ('HTTP_' + response.status);
                    throw failure(code, describeClaimFailure(code, username));
                });
        });
    }

    function storeIdentity(keypair, password, nip05) {
        return global.NostrCrypto.buildEncryptedIdentity(keypair.nsec, password)
            .then(function (identity) {
                identity.nip05 = nip05;
                global.APP.saveIdentity(identity);
                return identity;
            });
    }

    // The profile carries the handle and nothing else. NostrPublish derives the
    // name from the nip05 local part, so the user reads as "alice" in other
    // clients rather than as a raw key, without a name being invented for them.
    function publishProfile(identity, keypair, relayUrls) {
        var unsigned = global.NostrPublish.buildProfileEvent({ nip05: identity.nip05 });
        var signed = global.NostrCrypto.signEvent(unsigned, keypair.privateKeyHex);
        return global.NostrPublish.publish(new global.NostrTools.SimplePool(), relayUrls, signed);
    }

    function publishInitialProfile(identity, keypair, relayUrls, outcome) {
        if (!relayUrls.length) {
            outcome.publishError = 'no write relay configured';
            return Promise.resolve(outcome);
        }
        return publishProfile(identity, keypair, relayUrls)
            .then(function (results) {
                outcome.published = results.filter(function (r) { return r.accepted; }).length;
                return outcome;
            })
            .catch(function (err) {
                // A relay that will not take the profile does not undo a claimed
                // handle: the account exists either way, so this is reported
                // rather than thrown.
                outcome.publishError = message(err);
                return outcome;
            });
    }

    /**
     * Registers a new identity: mints a key, signs in with it, claims the handle,
     * and only then encrypts the key and stores it in this browser.
     *
     * The order is the point. Storing first and claiming afterwards — which is
     * what this replaced — leaves a browser holding an identity that asserts a
     * NIP-05 the directory has given to somebody else.
     *
     * Resolves with `{nsec, nip05, published, relayCount, publishError}`; the
     * nsec is for the backup screen. Rejects with an Error carrying `code` if
     * anything before the store fails, in which case nothing has been stored.
     */
    function submit(form) {
        var outcome = { nsec: null, nip05: null, published: 0, relayCount: 0, publishError: null };
        var keypair;
        var relays;

        return signedInKeypair()
            .then(function (signedIn) {
                keypair = signedIn;
                return global.APP.systemRelays();
            })
            .then(function (relayUrls) {
                relays = relayUrls;
                outcome.relayCount = relayUrls.length;
                return claimHandle(form.username, relayUrls);
            })
            .then(function () {
                outcome.nsec = keypair.nsec;
                outcome.nip05 = form.username + '@' + form.domain;
                return storeIdentity(keypair, form.password, outcome.nip05);
            })
            .then(function (identity) {
                // The attempt is over: a further submit belongs to a new user.
                pending = null;
                return publishInitialProfile(identity, keypair, relays, outcome);
            });
    }

    /**
     * Forgets the key held for an in-flight attempt. Called when the visitor
     * leaves registration and starts over, so the next attempt is a new
     * identity rather than a continuation of the abandoned one.
     */
    function reset() {
        pending = null;
    }

    var api = { submit: submit, reset: reset };

    global.Registration = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
