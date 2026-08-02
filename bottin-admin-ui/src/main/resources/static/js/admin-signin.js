// Administrator sign-in for the bottin dashboard.
//
// The private key is supplied once per device, kept encrypted under a
// passphrase, and used to prove control of itself. Neither the key nor the
// passphrase is ever sent to the deployment: what crosses the wire is a signed
// challenge, worthless once it has expired or been used.
//
// Key encryption and the handshake come from bottin-web-assets, shared with the
// client, so there is one implementation of each rather than two that drift.
var AdminSignIn = {

    // Distinct from the client's 'imani.identity.' keys: the two applications may
    // be served from the same host during development, and local storage is not
    // isolated by port.
    STORAGE_KEY: 'bottin.admin.identity',

    /**
     * The encrypted identity this device holds, or null. Decides which form the
     * sign-in page shows: the key and a new passphrase, or the passphrase alone.
     */
    stored: function () {
        var raw = localStorage.getItem(AdminSignIn.STORAGE_KEY);
        return raw ? JSON.parse(raw) : null;
    },

    /**
     * Discards the stored identity. The way back from a forgotten passphrase,
     * which cannot be recovered because nothing that could decrypt the key
     * leaves this browser.
     */
    forget: function () {
        localStorage.removeItem(AdminSignIn.STORAGE_KEY);
    },

    /**
     * First sign-in on this device: encrypt the key under the passphrase, keep
     * it here, and prove control of it.
     *
     * A key the deployment refuses is discarded before the failure is reported.
     * Leaving it would mean the next visit shows an unlock prompt that cannot
     * succeed, with no obvious way out.
     */
    firstSignIn: function (nsec, passphrase) {
        return NostrCrypto.buildEncryptedIdentity(nsec, passphrase).then(function (identity) {
            localStorage.setItem(AdminSignIn.STORAGE_KEY, JSON.stringify(identity));

            var hexKey = NostrCrypto.nsecToHex(nsec);
            return NapClient.login(hexKey, identity.npub).catch(function (error) {
                AdminSignIn.forget();
                throw error;
            });
        });
    },

    /**
     * Signing in again on a device that already holds the key: the passphrase
     * unlocks it, and the key itself is never asked for again.
     *
     * Used both when returning to the dashboard and when a session expires
     * mid-work.
     *
     * Unlike firstSignIn, a failed handshake here leaves the stored key alone. A
     * key that was accepted once is worth keeping through an unreachable server
     * or an expired session; discarding it would send the administrator looking
     * for their nsec because the network blipped. A key that has genuinely
     * stopped being accepted is handled by forget(), which the page offers.
     */
    unlock: function (passphrase) {
        var identity = AdminSignIn.stored();
        if (!identity) {
            return Promise.reject(new Error('There is no stored key on this device.'));
        }

        return NostrCrypto.verifyPassword(identity.passwordHash, identity.passwordSalt, passphrase)
            .then(function (valid) {
                // Checked before decryption is attempted, so a wrong passphrase
                // costs nothing and fails for a reason the page can state.
                if (!valid) {
                    throw new Error('Wrong passphrase.');
                }
                return NostrCrypto.decryptPrivateKey(
                    identity.privateKeyEncrypted,
                    identity.privateKeyIv,
                    identity.privateKeySalt,
                    passphrase
                );
            })
            .then(function (hexKey) {
                return NapClient.login(hexKey, identity.npub);
            });
    }
};

window.AdminSignIn = AdminSignIn;

if (typeof module !== 'undefined' && module.exports) {
    module.exports = AdminSignIn;
}
