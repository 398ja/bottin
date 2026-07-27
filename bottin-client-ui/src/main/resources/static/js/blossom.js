(function (root) {
  'use strict';

  var MAX_BYTES = 5 * 1024 * 1024;
  var AUTH_TTL_SECONDS = 300;

  // Lowercase hex string for the digest.
  function bytesToHex(bytes) {
    var hex = '';
    for (var i = 0; i < bytes.length; i++) {
      hex += bytes[i].toString(16).padStart(2, '0');
    }
    return hex;
  }

  // Blossom addresses a blob by the SHA-256 of its bytes. The auth event is
  // bound to the same hash, so a captured header cannot be reused.
  function sha256Hex(file) {
    return file.arrayBuffer()
      .then(function (buffer) { return crypto.subtle.digest('SHA-256', buffer); })
      .then(function (digest) { return bytesToHex(new Uint8Array(digest)); });
  }

  function buildAuthEvent(hashHex, expirationSeconds) {
    var now = Math.floor(Date.now() / 1000);
    return {
      kind: 24242,
      created_at: now,
      content: 'Upload image',
      tags: [
        ['t', 'upload'],
        ['x', hashHex],
        ['expiration', String(now + expirationSeconds)]
      ]
    };
  }

  function encodeAuthHeader(signedEvent) {
    return 'Nostr ' + btoa(JSON.stringify(signedEvent));
  }

  // Returns a string reason why a file cannot be uploaded, or null if it is acceptable.
  // Validation lives here so both call sites share one rule set.
  function rejectionReason(file) {
    if (!file || (file.type || '').indexOf('image/') !== 0) return 'Choose an image file.';
    if (file.size > MAX_BYTES) return 'Image must be 5 MB or smaller.';
    return null;
  }

  // Uploads to the BUD-02 `PUT /upload` endpoint, authenticated with a kind-24242
  // event that the caller signs, and resolves with the blob descriptor.
  // `signer` takes an unsigned event and returns a signed one, keeping this
  // module free of any dependency on how key material is held.
  function upload(file, blossomUrl, signer) {
    var reason = rejectionReason(file);
    if (reason) return Promise.reject(new Error(reason));
    return sha256Hex(file)
      .then(function (hashHex) { return signer(buildAuthEvent(hashHex, AUTH_TTL_SECONDS)); })
      .then(function (signedEvent) {
        return fetch(blossomUrl.replace(/\/+$/, '') + '/upload', {
          method: 'PUT',
          headers: { Authorization: encodeAuthHeader(signedEvent) },
          body: file
        });
      })
      .then(function (response) {
        if (!response.ok) {
          var reason = response.headers.get('X-Reason') || ('HTTP ' + response.status);
          throw new Error('Upload rejected: ' + reason);
        }
        return response.json();
      });
  }

  var api = {
    MAX_BYTES: MAX_BYTES,
    sha256Hex: sha256Hex,
    buildAuthEvent: buildAuthEvent,
    encodeAuthHeader: encodeAuthHeader,
    rejectionReason: rejectionReason,
    upload: upload
  };

  root.BlossomUpload = api;
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  }
})(typeof window !== 'undefined' ? window : globalThis);
