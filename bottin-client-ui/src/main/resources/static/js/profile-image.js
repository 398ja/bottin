(function (global) {
    // Binds one image field: a file input, the img that previews it, and the
    // error slot under it. Both the profile page and the onboarding step use
    // this twice, differing only in how they resolve a signer and where the
    // resulting URL is stored.
    function bind(config) {
        var input = document.getElementById(config.fileInputId);
        if (!input) return;
        var preview = document.getElementById(config.previewId);
        var error = config.errorId ? document.getElementById(config.errorId) : null;

        function showError(message) {
            if (!error) return;
            error.textContent = message;
            error.className = 'form-error';
        }

        function clearError() {
            if (!error) return;
            error.textContent = '';
            error.className = 'form-error hidden';
        }

        function setPreview(src) {
            if (!preview) return;
            preview.src = src;
            preview.classList.remove('hidden');
        }

        // A failed pick restores the last settled src, but most fields start
        // with none. Restoring nothing means clearing the src outright, or the
        // preview keeps rendering the object URL the failure path just revoked.
        function restorePreview(src) {
            if (!preview) return;
            if (src) {
                setPreview(src);
                return;
            }
            preview.removeAttribute('src');
            preview.classList.add('hidden');
        }

        // APP is a global loaded by app.js; guard it the way this file's own
        // IIFE guards window/module, so the field still works, minus toasts
        // and URL validation, wherever app.js was not loaded first.
        function hasApp() {
            return typeof APP !== 'undefined' && !!APP;
        }

        // Blossom's response is a string a third-party server returned, not one
        // the user typed, so it gets the same scheme check every other image
        // sink applies before it is trusted as a src.
        function validatedUrl(url) {
            return hasApp() && APP.safeImageUrl ? APP.safeImageUrl(url) : url;
        }

        function toast(message, type) {
            if (hasApp() && APP.showToast) APP.showToast(message, type);
        }

        // The last src known to be good (never a blob: URL): the field's initial
        // src, or the previous successful upload. A failed upload restores to
        // this, not to whatever the preview currently shows, so a rapid re-pick
        // never restores an in-flight pick's already-revoked object URL.
        var settledSrc = preview ? preview.getAttribute('src') : null;

        // The object URL of a pick whose upload has not yet settled, so a second
        // pick can revoke it immediately instead of leaking it.
        var pendingObjectUrl = null;

        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            if (!file) return;
            clearError();

            var reason = BlossomUpload.rejectionReason(file);
            if (reason) {
                showError(reason);
                input.value = '';
                return;
            }

            if (pendingObjectUrl) {
                URL.revokeObjectURL(pendingObjectUrl);
            }
            var restoreSrc = settledSrc;
            var objectUrl = URL.createObjectURL(file);
            pendingObjectUrl = objectUrl;
            setPreview(objectUrl);

            function releaseObjectUrl() {
                URL.revokeObjectURL(objectUrl);
                if (pendingObjectUrl === objectUrl) pendingObjectUrl = null;
            }

            // Wrapped so a signer that throws synchronously (e.g. onboarding's
            // nsec decode) rejects the chain instead of escaping it uncaught.
            Promise.resolve().then(function () {
                return config.resolveSigner();
            })
                .then(function (signer) {
                    return BlossomUpload.upload(file, config.blossomUrl, signer);
                })
                .then(function (blob) {
                    var safeUrl = validatedUrl(blob.url);
                    if (!safeUrl) throw new Error('Upload returned an unusable URL.');
                    releaseObjectUrl();
                    settledSrc = safeUrl;
                    setPreview(safeUrl);
                    config.onUploaded(safeUrl);
                    toast('Image uploaded', 'success');
                })
                .catch(function (err) {
                    releaseObjectUrl();
                    restorePreview(restoreSrc);
                    input.value = '';
                    // A dismissed unlock prompt is a deliberate no-op, not a failure.
                    // app.js tags the cancellation error with this flag on purpose;
                    // match profile.js rather than comparing the message text.
                    if (err && err.cancelled) return;
                    var message = err && err.message ? err.message : String(err);
                    toast('Upload failed: ' + message, 'error');
                });
        });
    }

    var api = { bind: bind };

    global.ProfileImage = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
