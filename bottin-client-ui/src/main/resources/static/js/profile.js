document.addEventListener('DOMContentLoaded', function () {
    var saveBtn = document.getElementById('profile-save-btn');
    if (!saveBtn) return;

    var userId = APP.getIdentityUserId();
    var identity = userId ? APP.loadIdentity(userId) : null;
    if (!identity) return;

    var fieldIds = {
        display_name: 'profile-display-name',
        about: 'profile-about',
        picture: 'profile-picture',
        banner: 'profile-banner',
        lud16: 'profile-lud16',
        website: 'profile-website'
    };

    function el(id) { return document.getElementById(id); }

    // Populate form and read-only fields from stored identity.
    el('profile-display-name').value = identity.displayName || '';
    el('profile-about').value = identity.about || '';
    el('profile-picture').value = identity.picture || '';
    el('profile-banner').value = identity.banner || '';
    el('profile-lud16').value = identity.lud16 || '';
    el('profile-website').value = identity.website || '';
    el('profile-nip05').value = identity.nip05 || '';
    el('profile-npub').value = identity.npub || '';
    el('profile-preview-name').textContent = identity.displayName || identity.npub || '';
    el('profile-nip05-display').textContent = identity.nip05 || '';

    var pictureUrl = APP.safeImageUrl(identity.picture);
    var avatar = el('profile-preview-avatar');
    avatar.onerror = function () { this.src = '/img/default-avatar.svg'; };
    if (pictureUrl) avatar.src = pictureUrl;

    el('profile-npub-copy').addEventListener('click', function () {
        var npub = el('profile-npub').value;
        if (!npub) return;
        var btn = this;
        navigator.clipboard.writeText(npub);
        btn.textContent = 'Copied!';
        setTimeout(function () { btn.textContent = 'Copy npub'; }, 2000);
    });

    function readFields() {
        return {
            display_name: el('profile-display-name').value.trim(),
            about: el('profile-about').value.trim(),
            picture: el('profile-picture').value.trim(),
            banner: el('profile-banner').value.trim(),
            lud16: el('profile-lud16').value.trim(),
            website: el('profile-website').value.trim(),
            nip05: el('profile-nip05').value.trim()
        };
    }

    function clearErrors() {
        Object.keys(fieldIds).forEach(function (k) {
            var errEl = el('profile-error-' + k.replace('_', '-'));
            if (errEl) errEl.className = 'form-error hidden';
        });
    }

    function showErrors(errors) {
        Object.keys(errors).forEach(function (k) {
            var errEl = el('profile-error-' + k.replace('_', '-'));
            if (errEl) {
                errEl.textContent = errors[k];
                errEl.className = 'form-error';
            }
        });
    }

    saveBtn.addEventListener('click', function () {
        clearErrors();
        var fields = readFields();
        var validation = NostrValidate.validateProfileFields(fields);
        if (!validation.valid) {
            showErrors(validation.errors);
            return;
        }

        // Persist locally first so change never lost if publishing fails.
        identity.displayName = fields.display_name;
        identity.about = fields.about;
        identity.picture = fields.picture;
        identity.banner = fields.banner;
        identity.lud16 = fields.lud16;
        identity.website = fields.website;
        APP.saveIdentity(identity);

        // Seed the default relay list on first use so a fresh profile can publish
        // out of the box, then publish to the configured write relays.
        APP.ensureRelaysSeeded(userId).then(function (relays) {
            var writeRelays = relays.filter(function (r) { return r.write; })
                .map(function (r) { return r.url; });
            if (!writeRelays.length) {
                APP.showToast('Add at least one write relay in Settings → Relays.', 'error');
                return;
            }
            return APP.ensureUnlocked(userId).then(function (hexKey) {
                var unsigned = NostrPublish.buildProfileEvent(fields);
                var signed = NostrCrypto.signEvent(unsigned, hexKey);
                return NostrPublish.publish(new NostrTools.SimplePool(), writeRelays, signed);
            }).then(function (results) {
                var accepted = results.filter(function (r) { return r.accepted; }).length;
                if (accepted) {
                    APP.showToast('Published to ' + accepted + '/' + results.length + ' relays', 'success');
                } else {
                    APP.showToast('Publish failed on all relays', 'error');
                }
            });
        }).catch(function (err) {
            // Cancelling the unlock is a deliberate no-op: the local save is retained.
            if (err && err.cancelled) return;
            APP.showToast('Publish failed: ' + (err && err.message ? err.message : err), 'error');
        });
    });
});
