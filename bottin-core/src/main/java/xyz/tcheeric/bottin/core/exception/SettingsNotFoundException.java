package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when the singleton settings row is absent.
 *
 * <p>The {@code V4__settings} migration inserts the row, so this cannot occur in
 * a correctly migrated deployment. It exists so that the impossible case fails
 * loudly instead of synthesising defaults, which would reintroduce the very
 * problem the settings table removes: configuration arriving from two sources.
 */
public class SettingsNotFoundException extends BottinException {

    private static final String ERROR_CODE = "SETTINGS_NOT_FOUND";
    private static final String DEFAULT_SUGGESTION =
            "Verify the V4__settings migration ran. The settings row is seeded by the migration and must always exist.";

    public SettingsNotFoundException() {
        super(ERROR_CODE, false,
                "Settings not found. The singleton settings row seeded by the V4 migration is missing",
                DEFAULT_SUGGESTION);
    }
}
