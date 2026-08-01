package xyz.tcheeric.bottin.admin.security;

import lombok.extern.slf4j.Slf4j;
import nostr.crypto.bech32.Bech32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.nap.core.AclDecision;
import xyz.tcheeric.nap.server.AclResolver;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides who administers this deployment.
 *
 * <p>This is the single point at which that question is answered. Every admin
 * route is gated on the permissions granted here, so a second place making the
 * same decision would be a second place to get it wrong — and the follow-up
 * feature that adds further administrators extends this class rather than
 * revisiting every route.
 *
 * <p>The administrator's public key is deployment configuration rather than
 * stored data, because it is what admits an operator when the database is
 * empty, wrong, or freshly restored. A public key is not a secret, which is
 * what makes it safe to hold where a password should not be.
 *
 * <p>The three ways to be refused are kept distinct. "No key configured" and
 * "the configured value is not a key" are operator mistakes with different
 * fixes, and neither should look like "your key is not the administrator's".
 */
@Component
@Slf4j
public class ConfiguredAdminAclResolver implements AclResolver {

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    private static final String NPUB_PREFIX = "npub1";

    /**
     * Everything the dashboard can do. The super administrator is the only role
     * this feature grants; the follow-up adds one holding all but
     * {@link AdminPermissions#MANAGE_ADMINS}.
     */
    private static final List<String> SUPER_ADMIN_PERMISSIONS =
            List.of(AdminPermissions.READ, AdminPermissions.WRITE, AdminPermissions.MANAGE_ADMINS);

    /**
     * The configured administrator in canonical hex, or null when the
     * configuration is absent or unusable — in which case {@link #keyState}
     * says which.
     */
    private final String administratorHex;

    private final AdminKeyState keyState;

    public ConfiguredAdminAclResolver(@Value("${bottin.admin.npub:}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            this.administratorHex = null;
            this.keyState = AdminKeyState.NOT_CONFIGURED;
            log.warn("admin_key_configuration state=not_configured impact=no administrator can sign in");
            return;
        }

        String hex = toCanonicalHex(configuredKey.trim());
        this.administratorHex = hex;
        this.keyState = hex == null ? AdminKeyState.UNREADABLE : AdminKeyState.CONFIGURED;

        if (hex == null) {
            log.error("admin_key_configuration state=unreadable impact=no administrator can sign in");
        } else {
            log.info("admin_key_configuration state=configured pubkey={}", hex);
        }
    }

    /**
     * Whether this deployment has a usable administrator key.
     *
     * <p>Read by the sign-in page so that it offers a form only when one could
     * succeed, and so that "no key configured" and "the value is not a key" are
     * reported as the different problems they are. The page and this resolver
     * therefore cannot disagree about the deployment's state.
     */
    public AdminKeyState keyState() {
        return keyState;
    }

    @Override
    public AclDecision resolve(String appId, String pubkey) {
        if (!AdminPermissions.APP_ID.equals(appId)) {
            log.warn("admin_signin_rejected reason=wrong_application app_id={}", appId);
            return AclDecision.denied("wrong_application");
        }

        if (administratorHex == null) {
            String reason = keyState == AdminKeyState.NOT_CONFIGURED
                    ? "no_admin_key_configured"
                    : "admin_key_unreadable";
            log.warn("admin_signin_rejected reason={}", reason);
            return AclDecision.denied(reason);
        }

        if (pubkey == null || !administratorHex.equalsIgnoreCase(pubkey.trim())) {
            log.warn("admin_signin_rejected reason=not_authorised pubkey={}", pubkey);
            return AclDecision.denied("not_authorised");
        }

        log.info("admin_signin_succeeded pubkey={} role={}", pubkey, AdminPermissions.SUPER_ADMIN);
        return AclDecision.allowed(List.of(AdminPermissions.SUPER_ADMIN), SUPER_ADMIN_PERMISSIONS);
    }

    /**
     * Normalises an {@code npub} or hex public key to canonical lowercase hex,
     * or returns null when the value is not a public key at all.
     *
     * <p>Bech32 decoding is nostr-java's; Principle VI forbids hand-rolling it.
     */
    private static String toCanonicalHex(String configuredKey) {
        String candidate = configuredKey.toLowerCase();

        if (candidate.startsWith(NPUB_PREFIX)) {
            try {
                candidate = Bech32.fromBech32(candidate).toLowerCase();
            } catch (Exception e) {
                return null;
            }
        }

        return HEX_64.matcher(candidate).matches() ? candidate : null;
    }
}
