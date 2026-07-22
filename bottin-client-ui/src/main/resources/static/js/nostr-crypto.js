var NostrCrypto = (function() {
    var NT = window.NostrTools;

    function bytesToHex(bytes) {
        if (typeof bytes === 'string') return bytes;
        return Array.from(bytes).map(function(b) { return b.toString(16).padStart(2, '0'); }).join('');
    }

    function hexToBytes(hex) {
        var bytes = [];
        for (var i = 0; i < hex.length; i += 2) bytes.push(parseInt(hex.substring(i, i + 2), 16));
        return new Uint8Array(bytes);
    }

    function base64ToBytes(base64) {
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes;
    }

    function bytesToBase64(bytes) {
        var binary = '';
        for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary);
    }

    return {
        generateKeypair: function() {
            var secretKey = NT.generateSecretKey();
            var privateKeyHex = bytesToHex(secretKey);
            var publicKeyHex = NT.getPublicKey(secretKey);
            return {
                nsec: NT.nip19.nsecEncode(secretKey),
                npub: NT.nip19.npubEncode(publicKeyHex),
                privateKeyHex: privateKeyHex,
                publicKeyHex: publicKeyHex
            };
        },

        nsecToNpub: function(nsec) {
            var decoded = NT.nip19.nsecDecode(nsec);
            var publicKeyHex = NT.getPublicKey(decoded.data);
            return NT.nip19.npubEncode(publicKeyHex);
        },

        nsecToHex: function(nsec) {
            var decoded = NT.nip19.nsecDecode(nsec);
            return bytesToHex(decoded.data);
        },

        hexToNsec: function(hex) {
            return NT.nip19.nsecEncode(hexToBytes(hex));
        },

        encryptPrivateKey: async function(privateKeyHex, password) {
            var encoder = new TextEncoder();
            var salt = crypto.getRandomValues(new Uint8Array(16));
            var iv = crypto.getRandomValues(new Uint8Array(12));

            var keyMaterial = await crypto.subtle.importKey(
                'raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']
            );

            var aesKey = await crypto.subtle.deriveKey(
                { name: 'PBKDF2', salt: salt, iterations: 100000, hash: 'SHA-256' },
                keyMaterial,
                { name: 'AES-GCM', length: 256 },
                false,
                ['encrypt']
            );

            var encrypted = await crypto.subtle.encrypt(
                { name: 'AES-GCM', iv: iv },
                aesKey,
                encoder.encode(privateKeyHex)
            );

            var passwordHash = await crypto.subtle.digest(
                'SHA-256', encoder.encode(password + bytesToHex(salt))
            );

            return {
                encrypted: bytesToBase64(new Uint8Array(encrypted)),
                iv: bytesToBase64(iv),
                salt: bytesToBase64(salt),
                passwordHash: bytesToBase64(new Uint8Array(passwordHash)),
                passwordSalt: bytesToBase64(salt)
            };
        },

        decryptPrivateKey: async function(encryptedBase64, ivBase64, saltBase64, password) {
            var encrypted = base64ToBytes(encryptedBase64);
            var iv = base64ToBytes(ivBase64);
            var salt = base64ToBytes(saltBase64);
            var encoder = new TextEncoder();

            var keyMaterial = await crypto.subtle.importKey(
                'raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']
            );

            var aesKey = await crypto.subtle.deriveKey(
                { name: 'PBKDF2', salt: salt, iterations: 100000, hash: 'SHA-256' },
                keyMaterial,
                { name: 'AES-GCM', length: 256 },
                false,
                ['decrypt']
            );

            var decrypted = await crypto.subtle.decrypt(
                { name: 'AES-GCM', iv: iv },
                aesKey,
                encrypted
            );

            return new TextDecoder().decode(decrypted);
        },

        verifyPassword: async function(passwordHashBase64, passwordSaltBase64, password) {
            var salt = base64ToBytes(passwordSaltBase64);
            var encoder = new TextEncoder();
            var hash = await crypto.subtle.digest('SHA-256', encoder.encode(password + bytesToHex(salt)));
            return bytesToBase64(new Uint8Array(hash)) === passwordHashBase64;
        },

        signNip98Event: function(challenge, challengeId, authUrl, method, nsecHex) {
            var now = Math.floor(Date.now() / 1000);
            var publicKeyHex = NT.getPublicKey(nsecHex);

            var event = {
                kind: 27235,
                pubkey: publicKeyHex,
                created_at: now,
                content: '',
                tags: [
                    ['u', authUrl],
                    ['method', method],
                    ['challenge', challenge],
                    ['challenge_id', challengeId]
                ]
            };

            var signed = NT.finalizeEvent(event, hexToBytes(nsecHex));
            return btoa(JSON.stringify(signed));
        },

        reEncryptPrivateKey: async function(oldPassword, newPassword, storedIdentity) {
            var privateKeyHex = await this.decryptPrivateKey(
                storedIdentity.privateKeyEncrypted,
                storedIdentity.privateKeyIv,
                storedIdentity.privateKeySalt,
                oldPassword
            );
            return await this.encryptPrivateKey(privateKeyHex, newPassword);
        }
    };
})();
