package xyz.tcheeric.bottin.admin.security;

/**
 * What the deployment's administrator-key configuration amounts to.
 *
 * <p>Exists so that the sign-in page and the access decision read the same
 * source and cannot disagree: a page inviting someone to sign in while the
 * resolver refuses everybody would leave an operator with no way to tell a
 * wrong key from a missing one.
 */
public enum AdminKeyState {

    /** A usable administrator public key is configured. */
    CONFIGURED,

    /** No administrator public key is set. Nobody can sign in. */
    NOT_CONFIGURED,

    /** A value is set but is not a usable public key. Nobody can sign in. */
    UNREADABLE
}
