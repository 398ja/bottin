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
    }
};

window.AdminSignIn = AdminSignIn;

if (typeof module !== 'undefined' && module.exports) {
    module.exports = AdminSignIn;
}
