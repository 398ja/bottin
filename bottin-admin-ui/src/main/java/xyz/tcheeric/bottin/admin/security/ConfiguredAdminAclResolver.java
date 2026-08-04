package xyz.tcheeric.bottin.admin.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.core.model.AdminRole;
import xyz.tcheeric.bottin.service.AdminUserService;
import xyz.tcheeric.bottin.core.nostr.NostrPublicKeys;
import xyz.tcheeric.nap.core.AclDecision;
import xyz.tcheeric.nap.server.AclResolver;
import xyz.tcheeric.nap.server.acl.PermissionRegistry;

import java.util.List;
import java.util.Optional;

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
 * <p>What each role <em>may do</em> is not decided here. This class decides which
 * role a proven key holds; {@link xyz.tcheeric.bottin.admin.config.AdminPermissionRegistryConfig}
 * declares what that role grants, and is validated at startup. An added
 * administrator's session still never carries
 * {@link AdminPermissions#MANAGE_ADMINS} — the registry withholds it — so the
 * permission interceptor refuses them the management endpoints whether or not a
 * page offered the control.
 *
 * <p>The stored role is read rather than assumed. An entry recorded as
 * {@code READONLY} is admitted as such, which it was not while this class
 * granted every stored administrator the same fixed set.
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
 *
 * <p>That distinction decides more than the log line. Only "this key is not an
 * administrator" ends the sessions the key already holds; the configuration
 * faults refuse the request and leave them alone. A denial that could not read
 * what it needed is not evidence against the holder, and revoking on it would
 * turn an operator's typo into a forced re-signature for everyone at once.
 */
@Component
@Slf4j
public class ConfiguredAdminAclResolver implements AclResolver {

    /**
     * Which role holds which permission. Consulted rather than restated, so a
     * change to the registry reaches sign-in without an edit here, and a role
     * naming an undeclared permission stops the application at startup instead
     * of producing an unreachable route.
     */
    private final PermissionRegistry registry;

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
                                      AdminUserService storedAdministrators,
                                      PermissionRegistry registry) {
        this.storedAdministrators = storedAdministrators;
        this.registry = registry;

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
            return admit(provenHex, AdminPermissions.SUPER_ADMIN);
        }

        Optional<AdminRole> storedRole = storedAdministrators.roleOf(provenHex);
        if (storedRole.isPresent()) {
            return admit(provenHex, registryRoleKey(storedRole.get()));
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

        // Ends every session this key holds, not merely this request. Both
        // sources were read and neither names the key, so the answer is a
        // decision rather than a gap in what could be read — which is what
        // separates this from the two refusals above. Removal already revokes
        // explicitly; this is what covers the case nothing else does, a rotated
        // master key whose former holder still has a live session.
        log.warn("admin_signin_rejected reason=not_authorised pubkey={} sessions=revoked", provenHex);
        return AclDecision.denied("not_authorised", true);
    }

    /**
     * Admits a proven key under a role, with the permissions the registry says
     * that role holds.
     *
     * <p>A role the registry does not declare is refused rather than admitted
     * with an empty permission set. The two are not the same: an empty set would
     * pass every {@code allowed()} check while failing every route, which is far
     * harder to diagnose than a refusal that names the role.
     */
    private AclDecision admit(String provenHex, String roleKey) {
        Optional<List<String>> permissions = permissionsFor(roleKey);

        if (permissions.isEmpty()) {
            log.error("admin_signin_rejected reason=role_not_in_registry role={} pubkey={} "
                    + "impact=this administrator cannot sign in until the registry declares the role",
                    roleKey, provenHex);
            return AclDecision.denied("role_not_in_registry");
        }

        log.info("admin_signin_succeeded pubkey={} role={}", provenHex, roleKey);
        return AclDecision.allowed(List.of(roleKey), permissions.get());
    }

    /** The permissions the registry grants a role, or empty if it declares no such role. */
    private Optional<List<String>> permissionsFor(String roleKey) {
        return registry.roles().stream()
                .filter(role -> role.key().equals(roleKey))
                .findFirst()
                .map(role -> List.copyOf(role.permissions()));
    }

    /**
     * The registry key for a stored role.
     *
     * <p>An exhaustive switch with no default, so adding a constant to
     * {@link AdminRole} is a compile error here rather than a new role silently
     * inheriting an existing one's permissions.
     */
    private static String registryRoleKey(AdminRole role) {
        return switch (role) {
            case ADMIN -> AdminPermissions.ADMIN;
            case READONLY -> AdminPermissions.READONLY;
        };
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
