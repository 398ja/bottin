package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when a removal names a key that is not an administrator.
 *
 * <p>Removing a key that is not there could reasonably be treated as already
 * achieving what was asked. It is not, because the two ways to arrive here are a
 * stale page and a mistyped key, and both are worth seeing: a removal that
 * quietly succeeds without removing anything leaves the operator believing
 * access was withdrawn when it was not.
 *
 * <p>This is the opposite treatment from <em>adding</em> a key that already
 * administers the deployment, which is a no-op. The asymmetry is deliberate —
 * there the end state the operator wanted already holds, and here it does not.
 */
public class AdministratorNotFoundException extends BottinException {

    private static final String ERROR_CODE = "ADMINISTRATOR_NOT_FOUND";
    private static final String DEFAULT_SUGGESTION =
            "Reload the settings page to see the current administrators. The key may already have been removed, "
                    + "or the key entered may not be the one intended.";

    public AdministratorNotFoundException(String pubkey) {
        super(ERROR_CODE, false,
                "Administrator not removed. No administrator is registered with public key " + pubkey,
                DEFAULT_SUGGESTION);
    }
}
