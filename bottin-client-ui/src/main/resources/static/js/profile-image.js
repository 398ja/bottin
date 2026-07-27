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

            var previousSrc = preview ? preview.getAttribute('src') : null;
            var objectUrl = URL.createObjectURL(file);
            setPreview(objectUrl);

            // Wrapped so a signer that throws synchronously (e.g. onboarding's
            // nsec decode) rejects the chain instead of escaping it uncaught.
            Promise.resolve().then(function () {
                return config.resolveSigner();
            })
                .then(function (signer) {
                    return BlossomUpload.upload(file, config.blossomUrl, signer);
                })
                .then(function (blob) {
                    URL.revokeObjectURL(objectUrl);
                    setPreview(blob.url);
                    config.onUploaded(blob.url);
                    APP.showToast('Image uploaded', 'success');
                })
                .catch(function (err) {
                    URL.revokeObjectURL(objectUrl);
                    if (preview && previousSrc) preview.src = previousSrc;
                    input.value = '';
                    // A dismissed unlock prompt is a deliberate no-op, not a failure.
                    // app.js tags the cancellation error with this flag on purpose;
                    // match profile.js rather than comparing the message text.
                    if (err && err.cancelled) return;
                    var message = err && err.message ? err.message : String(err);
                    APP.showToast('Upload failed: ' + message, 'error');
                });
        });
    }

    var api = { bind: bind };

    global.ProfileImage = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
