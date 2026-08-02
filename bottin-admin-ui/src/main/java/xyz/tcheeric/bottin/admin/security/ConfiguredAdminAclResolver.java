package xyz.tcheeric.bottin.admin.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.service.AdminUserService;
import xyz.tcheeric.bottin.service.NostrPublicKeys;
import xyz.tcheeric.nap.core.AclDecision;
import xyz.tcheeric.nap.server.AclResolver;

import java.util.List;

/**
 * Decides who administers this deployment.
 *
 * <p>This is the single point at which that question is answered. Every admin
 * route is gated on the permissions granted here, so a second place making the
 * same decision would be a second place to get it wrong.
 *
 * <p>Two sources are consulted, and the order is the design. The configured
 * master key is checked first and wins: its authority is deployment
 * configuration, which is what admits an operator when the database is empty,
 * wrong, or freshly restored. A stored entry for that same key — which the
 * interface will not create, but a later change of configuration could leave
 * behind — therefore cannot demote its holder. Only then is the stored list of
 * added administrators consulted.
 *
 * <p>The difference between the two roles lives here and nowhere else: an added
 * administrator's session never carries {@link AdminPermissions#MANAGE_ADMINS},
 * so the permission interceptor refuses them the management endpoints whether or
 * not a page offered the control.
 *
 * <p>A public key is not a secret, which is what makes it safe to hold in
 * configuration where a password should not be.
 *
 * <p>The ways to be refused are kept distinct. "No key configured" and "the
 * configured value is not a key" are operator mistakes with different fixes, and
 * neither should look like "your key is not an administrator's". Note that those
 * two are reported only after the stored list has been consulted: a deployment
 * whose master key is missing or unreadable still admits added administrators,
 * so a misconfiguration does not also revoke everybody else.
 */
@Component
@Slf4j
public class ConfiguredAdminAclResolver implements AclResolver {

    /**
     * Everything the dashboard can do. The super administrator is the only role
     * this feature grants; the follow-up adds one holding all but
     * {@link AdminPermissions#MANAGE_ADMINS}.
     */
    private static final List<String> SUPER_ADMIN_PERMISSIONS =
            List.of(AdminPermissions.READ, AdminPermissions.WRITE, AdminPermissions.MANAGE_ADMINS);

    /**
     * Everything an added administrator can do: the whole dashboard except
     * managing administrators. The absence of {@link AdminPermissions#MANAGE_ADMINS}
     * here is the entire difference between the two roles, and it is enforced by
     * the permission interceptor rather than by which controls a page renders.
     */
    private static final List<String> ADMIN_PERMISSIONS =
            List.of(AdminPermissions.READ, AdminPermissions.WRITE);

    /**
     * The configured administrator in canonical hex, or null when the
     * configuration is absent or unusable — in which case {@link #keyState}
     * says which.
     */
    private final String administratorHex;

    private final AdminKeyState keyState;

    /** The administrators added by the super administrator, consulted after configuration. */
    private final AdminUserService storedAdministrators;

    public ConfiguredAdminAclResolver(@Value("${bottin.admin.npub:}") String configuredKey,
                                      AdminUserService storedAdministrators) {
        this.storedAdministrators = storedAdministrators;

        if (configuredKey == null || configuredKey.isBlank()) {
            this.administratorHex = null;
            this.keyState = AdminKeyState.NOT_CONFIGURED;
            log.warn("admin_key_configuration state=not_configured impact=no super administrator can sign in");
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

    /**
     * Both arguments are the same identity: NAP supplies it as bech32 and as
     * hex. Neither names an application — there is nothing here to check a
     * caller's identity <em>against</em> beyond the configured key.
     */
    @Override
    public AclDecision resolve(String npub, String pubkey) {
        String provenHex = provenKeyAsHex(npub, pubkey);
        if (provenHex == null) {
            log.warn("admin_signin_rejected reason=no_key_proven");
            return AclDecision.denied("no_key_proven");
        }

        if (provenHex.equals(administratorHex)) {
            log.info("admin_signin_succeeded pubkey={} role={}", provenHex, AdminPermissions.SUPER_ADMIN);
            return AclDecision.allowed(List.of(AdminPermissions.SUPER_ADMIN), SUPER_ADMIN_PERMISSIONS);
        }

        if (storedAdministrators.isAdministrator(provenHex)) {
            log.info("admin_signin_succeeded pubkey={} role={}", provenHex, AdminPermissions.ADMIN);
            return AclDecision.allowed(List.of(AdminPermissions.ADMIN), ADMIN_PERMISSIONS);
        }

        if (administratorHex == null) {
            // Worth distinguishing: with no usable master key, nobody can manage
            // the administrator list, so an operator seeing this needs to fix
            // configuration rather than look for a missing entry.
            String reason = keyState == AdminKeyState.NOT_CONFIGURED
                    ? "no_admin_key_configured"
                    : "admin_key_unreadable";
            log.warn("admin_signin_rejected reason={} pubkey={}", reason, provenHex);
            return AclDecision.denied(reason);
        }

        log.warn("admin_signin_rejected reason=not_authorised pubkey={}", provenHex);
        return AclDecision.denied("not_authorised");
    }

    /**
     * The proven identity in canonical hex, or null when neither argument
     * carries one.
     *
     * <p>Each argument is normalised rather than trusted to hold the encoding
     * its name suggests, so the comparison holds whichever way round the two
     * arrive. This is not defensive padding: assuming the order is exactly what
     * refused every administrator in 0.7.0, and the assumption bought nothing
     * because both arguments denote the same key.
     */
    private static String provenKeyAsHex(String npub, String pubkey) {
        String fromPubkey = toCanonicalHex(pubkey);
        return fromPubkey != null ? fromPubkey : toCanonicalHex(npub);
    }

    /**
     * Canonical lowercase hex, or null when the value is not a public key.
     *
     * <p>Delegates to {@link NostrPublicKeys}, which is also what decides
     * whether a key being added is one the deployment already knows. Two copies
     * of this rule would let the page and the sign-in disagree about whether two
     * spellings are the same administrator.
     */
    private static String toCanonicalHex(String key) {
        return NostrPublicKeys.toCanonicalHex(key).orElse(null);
    }
}
