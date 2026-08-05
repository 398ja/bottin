document.addEventListener('DOMContentLoaded', function () {
    var saveBtn = document.getElementById('profile-save-btn');
    if (!saveBtn) return;

    var userId = APP.getIdentityUserId();
    var identity = userId ? APP.loadIdentity(userId) : null;
    if (!identity) return;

    // Only the keys are used (to clear/show error divs below); picture and
    // banner have no companion text-input id since they moved to file inputs.
    var fieldIds = {
        display_name: null,
        about: null,
        picture: null,
        banner: null,
        lud16: null,
        website: null
    };

    function el(id) { return document.getElementById(id); }

    // Populate form and read-only fields from stored identity.
    el('profile-display-name').value = identity.displayName || '';
    el('profile-about').value = identity.about || '';
    el('profile-lud16').value = identity.lud16 || '';
    el('profile-website').value = identity.website || '';
    el('profile-nip05').value = identity.nip05 || '';

    var pictureUrl = APP.safeImageUrl(identity.picture);
    if (pictureUrl) el('profile-preview-avatar').src = pictureUrl;

    var bannerUrl = APP.safeImageUrl(identity.banner);
    var bannerPreview = el('profile-preview-banner');
    if (bannerUrl) {
        bannerPreview.src = bannerUrl;
        bannerPreview.classList.remove('hidden');
    }

    var blossomUrl = (document.getElementById('blossom-url') || {}).textContent || '';

    // The unlock modal may already be open for another action; reuse the same
    // resolver so both image fields and the save button share one prompt.
    function resolveSigner() {
        return APP.ensureUnlocked(userId).then(function (hexKey) {
            return function (unsigned) {
                return NostrCrypto.signEvent(unsigned, hexKey);
            };
        });
    }

    function storeImageUrl(field, url) {
        identity[field] = url;
        APP.saveIdentity(identity);
    }

    ProfileImage.bind({
        fileInputId: 'profile-picture',
        previewId: 'profile-preview-avatar',
        errorId: 'profile-error-picture',
        blossomUrl: blossomUrl.trim(),
        resolveSigner: resolveSigner,
        onUploaded: function (url) { storeImageUrl('picture', url); }
    });

    ProfileImage.bind({
        fileInputId: 'profile-banner',
        previewId: 'profile-preview-banner',
        errorId: 'profile-error-banner',
        blossomUrl: blossomUrl.trim(),
        resolveSigner: resolveSigner,
        onUploaded: function (url) { storeImageUrl('banner', url); }
    });

    // Removing clears the stored URL only; the blob stays on the media server,
    // where the user can delete it with their own key.
    function bindRemove(buttonId, field, previewId, defaultSrc) {
        el(buttonId).addEventListener('click', function () {
            storeImageUrl(field, '');
            var preview = el(previewId);
            if (defaultSrc) {
                preview.src = defaultSrc;
            } else {
                preview.removeAttribute('src');
                preview.classList.add('hidden');
            }
            el('profile-' + field).value = '';
        });
    }

    bindRemove('profile-picture-remove', 'picture', 'profile-preview-avatar', '/img/default-avatar.svg');
    bindRemove('profile-banner-remove', 'banner', 'profile-preview-banner', null);

    function readFields() {
        return {
            display_name: el('profile-display-name').value.trim(),
            about: el('profile-about').value.trim(),
            picture: identity.picture || '',
            banner: identity.banner || '',
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
        identity.lud16 = fields.lud16;
        identity.website = fields.website;
        APP.saveIdentity(identity);

        // Publish to the user's own write relays together with the deployment's
        // system relays, so a profile reaches the deployment's relays even before
        // the user has added any of their own.
        APP.effectiveWriteRelays(userId).then(function (writeRelays) {
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
