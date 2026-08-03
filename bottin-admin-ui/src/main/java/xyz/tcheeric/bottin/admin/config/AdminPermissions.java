package xyz.tcheeric.bottin.admin.config;

/**
 * The permission and role keys the admin dashboard recognises.
 *
 * <p>One definition, referenced by {@link AdminPermissionRegistryConfig}, the ACL
 * resolver, and every {@code @RequiresPermission} annotation. A permission
 * spelled slightly differently in an annotation than in the registry would fail
 * closed silently, so the strings live here rather than at each use — and
 * {@code PermissionRegistryValidator} rejects at startup any role that claims a
 * permission not declared below.
 *
 * <p>Which role holds which permission is <em>not</em> here. That mapping is the
 * registry's, so it can be read in one place and validated at startup.
 */
public final class AdminPermissions {

    // ---- Permissions ------------------------------------------------------

    /** View the dashboard, records, domains, and settings. */
    public static final String READ = "admin:read";

    /** Create, change, and delete records and domains. */
    public static final String WRITE = "admin:write";

    /**
     * Change the deployment settings — relay topology and media server.
     *
     * <p>Separate from {@link #WRITE} because the blast radius differs in kind:
     * editing a NIP-05 record affects that record, while repointing the relays
     * affects every request the deployment serves, and applies without a
     * restart. Held by the super administrator alone — an added administrator
     * maintains records and domains but cannot repoint the deployment they
     * were added to.
     */
    public static final String SETTINGS_WRITE = "admin:settings-write";

    /** Add or remove administrators. Held by the super administrator alone. */
    public static final String MANAGE_ADMINS = "admin:manage-admins";

    // ---- Roles ------------------------------------------------------------

    /**
     * The configured master key. Exactly one per deployment, set in deployment
     * configuration rather than stored, because it is what admits an operator
     * when the database is empty, wrong, or freshly restored.
     */
    public static final String SUPER_ADMIN = "super-admin";

    /**
     * An administrator added by the super administrator. Full use of the
     * dashboard, but cannot manage the administrator list.
     */
    public static final String ADMIN = "admin";

    /**
     * An administrator who can see the dashboard and change nothing.
     *
     * <p>Corresponds to {@code AdminRole.READONLY}, which the {@code admin_users}
     * table has accepted since V1. No page offers the role when adding an
     * administrator, so it is reachable only by setting the column directly —
     * but a row that says {@code READONLY} is now honoured rather than silently
     * granted write access, which is what it was until this registry existed.
     */
    public static final String READONLY = "readonly";

    private AdminPermissions() {
    }
}
