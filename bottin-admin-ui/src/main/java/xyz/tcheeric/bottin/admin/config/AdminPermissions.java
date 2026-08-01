package xyz.tcheeric.bottin.admin.config;

/**
 * The permissions and roles the admin dashboard recognises.
 *
 * <p>One definition, referenced by the permission registry, the ACL resolver, and
 * every {@code @RequiresPermission} annotation. A permission spelled slightly
 * differently in an annotation than in the registry fails open or closed
 * silently, so the strings live here rather than at each use.
 *
 * <p>{@link #ADMIN} and {@link #MANAGE_ADMINS} are declared although nothing can
 * yet exercise them: no second administrator exists until the follow-up feature
 * adds one. Declaring the target shape now costs three constants and makes that
 * feature a change to this class and the resolver, rather than a revisit of
 * every annotated route.
 */
public final class AdminPermissions {

    /**
     * The application this registry belongs to. NAP scopes an ACL record by
     * application, so this must not collide with the client's.
     */
    public static final String APP_ID = "bottin-admin";

    /** View the dashboard, records, domains, and settings. */
    public static final String READ = "admin:read";

    /** Create, change, and delete records, domains, and settings. */
    public static final String WRITE = "admin:write";

    /** Add or remove administrators. Held by the super administrator alone. */
    public static final String MANAGE_ADMINS = "admin:manage-admins";

    /**
     * The configured master key. Exactly one per deployment, set in deployment
     * configuration rather than stored, because it is what admits an operator
     * when the database is empty, wrong, or freshly restored.
     */
    public static final String SUPER_ADMIN = "super-admin";

    /**
     * An administrator added by the super administrator. Full use of the
     * dashboard, but cannot manage the administrator list. Unused until the
     * follow-up feature.
     */
    public static final String ADMIN = "admin";

    private AdminPermissions() {
    }
}
