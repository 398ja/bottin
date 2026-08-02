// NAP (Nostr Authentication Protocol) client.
//
// Runs the challenge/sign/complete handshake and leaves the caller holding a
// session cookie. Shared by the client and the admin dashboard: both prove
// control of a Nostr key the same way, and the private key never leaves the
// browser in either.
//
// Replaces a stub whose three methods threw "Not implemented - Phase 2" while
// the working handshake lived inline in the client's app.js.
var NapClient = {

    // Asks the server for a challenge to sign. The npub is an assertion, not a
    // credential: the server issues a challenge for any well-formed key and
    // decides whether that key is welcome at completion, once it has been
    // proven. Answering differently here would tell an anonymous caller which
    // key the deployment accepts.
    init: function (npub) {
        return fetch('/api/v1/auth/init', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ npub: npub })
        }).then(function (response) {
            if (!response.ok) {
                throw NapClient.failure('Could not start authentication', response.status);
            }
            return response.json();
        });
    },

    // Presents the signed challenge. On success the server sets the session
    // cookie; the signed event is the proof, and it is worthless once its
    // challenge has expired or been used.
    complete: function (challengeId, signedEventBase64) {
        return fetch('/api/v1/auth/complete', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Nostr ' + signedEventBase64
            },
            body: JSON.stringify({ challenge_id: challengeId })
        }).then(function (response) {
            if (!response.ok) throw NapClient.failure('Authentication failed', response.status);
            return response;
        });
    },

    /**
     * An error carrying the status that caused it.
     *
     * <p>Callers need it to tell a refused key apart from a rate limit or an
     * unreachable deployment. Without it a page can only report one guess for
     * every failure, and the wrong guess sends an operator looking for a key
     * problem that is not there.
     */
    failure: function (message, status) {
        var error = new Error(message);
        error.status = status;
        return error;
    },

    // The whole handshake, given a decrypted key. Resolves on success and
    // rejects otherwise; the private key is used to sign and is never sent.
    login: function (hexKey, npub) {
        return NapClient.init(npub).then(function (challenge) {
            return NostrCrypto.signNip98Event(
                challenge.challenge, challenge.challenge_id, challenge.auth_url, 'POST', hexKey
            ).then(function (signedEvent) {
                return NapClient.complete(challenge.challenge_id, signedEvent);
            });
        });
    },

    // Ends the server-side session. Callers are responsible for also erasing
    // whatever they hold locally: a sign-out that ended the session but left an
    // encrypted key on the device would be a false reassurance.
    logout: function () {
        return fetch('/api/v1/auth/logout', {
            method: 'POST',
            credentials: 'same-origin'
        });
    }
};

if (typeof module !== 'undefined' && module.exports) {
    module.exports = NapClient;
}
