package xyz.tcheeric.bottin.service.port;

/**
 * Ends the sessions held by an administrator.
 *
 * <p>A port rather than a direct call into the session store, because the
 * session store belongs to the authentication library and the use-case layer
 * must not depend on it (Principle III). One implementation exists because there
 * is one session store, not because a second is anticipated.
 *
 * <p>It exists at all so that removing an administrator and ending their session
 * can be one operation in the service. Left to callers, the next one to remove an
 * administrator — a REST endpoint, a cleanup job, a test helper — would silently
 * not revoke, and the failure would be invisible until somebody kept working
 * after being removed.
 */
public interface AdministratorSessionRevoker {

    /**
     * Ends every session held by this key and returns how many were ended.
     *
     * <p>The count is returned so callers can log it. Zero where one was
     * expected is the visible symptom of a removal that did not take effect —
     * a key passed in the wrong encoding, or a session held by another instance.
     *
     * @param pubkeyHex canonical lowercase hex; the session store matches on the
     *                  hex principal, so a bech32 key would revoke nothing while
     *                  appearing to succeed
     */
    int revokeSessionsFor(String pubkeyHex);
}
