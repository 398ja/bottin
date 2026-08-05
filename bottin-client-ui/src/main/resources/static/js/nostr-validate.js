(function (global) {
    function isSafeHttpUrl(v) {
        if (!v) return true;
        try {
            var url = new URL(v);
            return url.protocol === 'https:' || url.protocol === 'http:';
        } catch (e) {
            return false;
        }
    }

    function isValidLud16(v) {
        if (!v) return true;
        return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v);
    }

    function isValidDisplayName(v) {
        if (!v) return true;
        return v.length <= 128;
    }

    function validateProfileFields(fields) {
        var f = fields || {};
        var errors = {};
        if (!isValidDisplayName(f.display_name)) {
            errors.display_name = 'Display name must be 128 characters or fewer.';
        }
        if (!isSafeHttpUrl(f.picture)) {
            errors.picture = 'Picture must be an http(s) URL.';
        }
        if (!isSafeHttpUrl(f.banner)) {
            errors.banner = 'Banner must be an http(s) URL.';
        }
        if (!isSafeHttpUrl(f.website)) {
            errors.website = 'Website must be an http(s) URL.';
        }
        if (!isValidLud16(f.lud16)) {
            errors.lud16 = 'Lightning address must look like name@domain.';
        }
        return { valid: Object.keys(errors).length === 0, errors: errors };
    }

    var api = {
        isSafeHttpUrl: isSafeHttpUrl,
        isValidLud16: isValidLud16,
        isValidDisplayName: isValidDisplayName,
        validateProfileFields: validateProfileFields
    };

    global.NostrValidate = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
