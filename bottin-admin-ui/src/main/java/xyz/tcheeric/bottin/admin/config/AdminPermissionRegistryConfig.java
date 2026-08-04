package xyz.tcheeric.bottin.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.tcheeric.nap.server.acl.PermissionDefinition;
import xyz.tcheeric.nap.server.acl.PermissionRegistry;
import xyz.tcheeric.nap.server.acl.PermissionRegistryValidator;
import xyz.tcheeric.nap.server.acl.RoleDefinition;

import java.util.List;
import java.util.Set;

/**
 * Declares which role holds which permission, and refuses to start if the
 * declaration is inconsistent.
 *
 * <p>Before this existed the mapping was two hardcoded lists inside the ACL
 * resolver, and a permission named in a {@code @RequiresPermission} annotation
 * but in no list produced a route nobody could reach — a denial indistinguishable
 * from a legitimate one, and permanent. {@code PermissionRegistryValidator}
 * turns that into a startup failure: a role claiming an undeclared permission,
 * a duplicate key, or a default role that names no declared role all throw
 * before the application serves anything.
 *
 * <p>Nothing in nap consumes this bean automatically — nap's auto-configuration
 * declares no {@code PermissionRegistry}. It is read by
 * {@code ConfiguredAdminAclResolver}, which expands a role into its permissions
 * rather than repeating the set.
 *
 * <p>Deliberately not using {@code RegistryAclResolver}: it resolves a principal
 * through an {@code AclStore}, which cannot express this deployment's master key,
 * whose authority is configuration rather than a stored row.
 */
@Configuration
public class AdminPermissionRegistryConfig {

    /**
     * Names this application within nap's ACL vocabulary. Only one application
     * is registered, so the value matters solely for legibility in logs.
     */
    static final String APP_ID = "bottin-admin";

    @Bean
    public PermissionRegistry adminPermissionRegistry() {
        PermissionRegistry registry = PermissionRegistry.of(
                APP_ID, permissions(), roles(), AdminPermissions.READONLY);

        // PermissionRegistry.of does not validate — only RegistryAclResolver.create
        // does, and this deployment does not use it. Validating here is what makes
        // the guarantee in this class's documentation true.
        PermissionRegistryValidator.validate(registry);
        return registry;
    }

    private static List<PermissionDefinition> permissions() {
        return List.of(
                permission(AdminPermissions.READ, "View the dashboard, records, domains, and settings"),
                permission(AdminPermissions.WRITE, "Create, change, and delete records"),
                permission(AdminPermissions.MANAGE_DOMAINS, "Add, verify, and remove domains"),
                permission(AdminPermissions.SETTINGS_WRITE, "Change relay topology and media server settings"),
                permission(AdminPermissions.MANAGE_ADMINS, "Add and remove administrators"));
    }

    /**
     * The roles, ordered from most to least authority.
     *
     * <p>{@link AdminPermissions#READONLY} is the default role because a
     * principal reaching a default at all means something has gone wrong with
     * the intended assignment, and the least authority is the right answer to
     * that.
     */
    private static List<RoleDefinition> roles() {
        return List.of(
                new RoleDefinition(AdminPermissions.SUPER_ADMIN,
                        "The configured master key. Everything, including managing administrators.",
                        Set.of(AdminPermissions.READ,
                                AdminPermissions.WRITE,
                                AdminPermissions.MANAGE_DOMAINS,
                                AdminPermissions.SETTINGS_WRITE,
                                AdminPermissions.MANAGE_ADMINS)),
                new RoleDefinition(AdminPermissions.ADMIN,
                        "An added administrator. Records within the deployment's domains, "
                                + "but neither which domains those are nor the deployment itself.",
                        Set.of(AdminPermissions.READ,
                                AdminPermissions.WRITE)),
                new RoleDefinition(AdminPermissions.READONLY,
                        "Sees the dashboard, changes nothing.",
                        Set.of(AdminPermissions.READ)));
    }

    /**
     * No permission requires step-up: nap's step-up flow demands a second proof
     * mid-session, and nothing in this dashboard has been judged to warrant one.
     */
    private static PermissionDefinition permission(String key, String description) {
        return new PermissionDefinition(key, description, false);
    }
}
