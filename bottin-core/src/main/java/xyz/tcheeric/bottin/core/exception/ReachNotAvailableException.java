package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when no reach figure is available for a requested profile.
 *
 * <p>Distinct from a calculated reach of zero: this indicates the profile has
 * never been processed by a scheduled calculation (or is not a tracked profile).
 */
public class ReachNotAvailableException extends BottinException {

    private static final String ERROR_CODE = "REACH_NOT_AVAILABLE";
    private static final String DEFAULT_SUGGESTION =
            "This profile may not be registered, or its reach has not been calculated yet. "
                    + "Try again after the next scheduled calculation.";

    public ReachNotAvailableException(String identifier) {
        super(ERROR_CODE, false,
                "No reach figure is available for the requested profile: " + identifier,
                DEFAULT_SUGGESTION);
    }
}
