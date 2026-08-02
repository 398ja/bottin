var NostrCrypto = (function() {
    var NT = window.NostrTools;

    function bytesToHex(bytes) {
        if (typeof bytes === 'string') return bytes;
        return Array.from(bytes).map(function(b) { return b.toString(16).padStart(2, '0'); }).join('');
    }

    async function sha256Hex(str) {
        var encoder = new TextEncoder();
        var data = encoder.encode(str);
        var hash = await crypto.subtle.digest('SHA-256', data);
        return bytesToHex(new Uint8Array(hash));
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
            var decoded = NT.nip19.decode(nsec);
            var publicKeyHex = NT.getPublicKey(decoded.data);
            return NT.nip19.npubEncode(publicKeyHex);
        },

        nsecToHex: function(nsec) {
            var decoded = NT.nip19.decode(nsec);
            return bytesToHex(decoded.data);
        },

        hexToNsec: function(hex) {
            return NT.nip19.nsecEncode(hexToBytes(hex));
        },

        encryptPrivateKey: async function(privateKeyHex, password) {
            var encoder = new TextEncoder();
            var salt = crypto.getRandomValues(new Uint8Array(16));
            var iv = crypto.getRandomValues(new Uint8Array(12));
            var verifySalt = crypto.getRandomValues(new Uint8Array(16));

            var keyMaterial = await crypto.subtle.importKey(
                'raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']
            );

            var aesKey = await crypto.subtle.deriveKey(
                { name: 'PBKDF2', salt: salt, iterations: 600000, hash: 'SHA-256' },
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

            var verifyKeyMaterial = await crypto.subtle.importKey(
                'raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits']
            );

            var verifyBits = await crypto.subtle.deriveBits(
                { name: 'PBKDF2', salt: verifySalt, iterations: 600000, hash: 'SHA-256' },
                verifyKeyMaterial,
                256
            );

            return {
                encrypted: bytesToBase64(new Uint8Array(encrypted)),
                iv: bytesToBase64(iv),
                salt: bytesToBase64(salt),
                passwordHash: bytesToBase64(new Uint8Array(verifyBits)),
                passwordSalt: bytesToBase64(verifySalt)
            };
        },

        buildEncryptedIdentity: async function(nsec, password) {
            var decoded = NT.nip19.decode(nsec);
            var privateKeyHex = bytesToHex(decoded.data);
            var pubkeyHex = NT.getPublicKey(decoded.data);
            var npub = NT.nip19.npubEncode(pubkeyHex);
            var encrypted = await this.encryptPrivateKey(privateKeyHex, password);
            return {
                userId: npub,
                npub: npub,
                pubkeyHex: pubkeyHex,
                privateKeyEncrypted: encrypted.encrypted,
                privateKeyIv: encrypted.iv,
                privateKeySalt: encrypted.salt,
                passwordHash: encrypted.passwordHash,
                passwordSalt: encrypted.passwordSalt,
                createdAt: Date.now()
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
                { name: 'PBKDF2', salt: salt, iterations: 600000, hash: 'SHA-256' },
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

            var keyMaterial = await crypto.subtle.importKey(
                'raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits']
            );

            var verifyBits = await crypto.subtle.deriveBits(
                { name: 'PBKDF2', salt: salt, iterations: 600000, hash: 'SHA-256' },
                keyMaterial,
                256
            );

            var computedHash = bytesToBase64(new Uint8Array(verifyBits));
            if (computedHash === passwordHashBase64) return true;

            var legacyHash = await crypto.subtle.digest('SHA-256', encoder.encode(password + bytesToHex(salt)));
            return bytesToBase64(new Uint8Array(legacyHash)) === passwordHashBase64;
        },

        signNip98Event: async function(challenge, challengeId, authUrl, method, nsecHex) {
            var now = Math.floor(Date.now() / 1000);
            var publicKeyHex = NT.getPublicKey(nsecHex);
            var body = JSON.stringify({ challenge_id: challengeId });
            var payloadHash = await sha256Hex(body);

            var event = {
                kind: 27235,
                pubkey: publicKeyHex,
                created_at: now,
                content: '',
                tags: [
                    ['u', authUrl],
                    ['method', method],
                    ['payload', payloadHash],
                    ['challenge', challenge],
                    ['challenge_id', challengeId]
                ]
            };

            var signed = NT.finalizeEvent(event, hexToBytes(nsecHex));
            return btoa(JSON.stringify(signed));
        },

        signEvent: function(unsignedEvent, nsecHex) {
            var template = {
                kind: unsignedEvent.kind,
                created_at: unsignedEvent.created_at || Math.floor(Date.now() / 1000),
                tags: unsignedEvent.tags || [],
                content: unsignedEvent.content || ''
            };
            return NT.finalizeEvent(template, hexToBytes(nsecHex));
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
