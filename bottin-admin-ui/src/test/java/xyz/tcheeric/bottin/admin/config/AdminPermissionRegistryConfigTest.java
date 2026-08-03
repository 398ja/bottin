package xyz.tcheeric.bottin.admin.config;

import org.junit.jupiter.api.Test;
import xyz.tcheeric.bottin.core.model.AdminRole;
import xyz.tcheeric.nap.server.acl.PermissionDefinition;
import xyz.tcheeric.nap.server.acl.PermissionRegistry;
import xyz.tcheeric.nap.server.acl.PermissionRegistryValidator;
import xyz.tcheeric.nap.server.acl.RoleDefinition;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the declared permission registry.
 *
 * <p>The registry's job is to fail loudly at startup rather than quietly at a
 * route, so these assert the declaration is internally consistent and that the
 * validator would actually catch it if it were not.
 */
class AdminPermissionRegistryConfigTest {

    private final PermissionRegistry registry =
            new AdminPermissionRegistryConfig().adminPermissionRegistry();

    /**
     * Tests that building the registry validates it — every role's permissions
     * are declared, no key is duplicated, and the default role exists. The bean
     * method throws if not, so reaching this assertion is the result.
     */
    @Test
    void shouldProduceAValidRegistry() {
        // When & Then: validating again is a no-op on an already-valid registry
        PermissionRegistryValidator.validate(registry);

        assertThat(registry.roles()).extracting(RoleDefinition::key)
                .containsExactlyInAnyOrder(AdminPermissions.SUPER_ADMIN,
                        AdminPermissions.ADMIN, AdminPermissions.READONLY);
    }

    /**
     * Tests that the validator this configuration relies on genuinely rejects a
     * role naming a permission nobody declared.
     *
     * <p>Without this the "startup validation" claim rests on the library
     * behaving as read rather than as observed — the exact assumption that let a
     * green suite ship an authorization check which refused every key.
     */
    @Test
    void shouldRejectARoleClaimingAnUndeclaredPermission() {
        // Given: a registry whose role names a permission that does not exist
        PermissionRegistry broken = PermissionRegistry.of(
                "test",
                List.of(new PermissionDefinition(AdminPermissions.READ, "Read", false)),
                List.of(new RoleDefinition("some-role", "Typo below",
                        Set.of("admin:raed"))),
                "some-role");

        // When & Then: validation refuses it, naming the offending permission
        assertThatThrownBy(() -> PermissionRegistryValidator.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin:raed");
    }

    /**
     * Tests that every permission the dashboard declares is held by at least one
     * role. A permission no role holds gates a route nobody can reach, which
     * looks identical to a correct refusal.
     */
    @Test
    void shouldGrantEveryDeclaredPermissionToSomeRole() {
        // Given: every permission any role holds
        Set<String> granted = registry.roles().stream()
                .flatMap(role -> role.permissions().stream())
                .collect(java.util.stream.Collectors.toSet());

        // When & Then
        assertThat(registry.permissions()).extracting(PermissionDefinition::key)
                .allSatisfy(key -> assertThat(granted).contains(key));
    }

    /**
     * Tests that every {@code @RequiresPermission} value used by the dashboard
     * is a permission the registry declares.
     *
     * <p>Derived from the constants rather than from a list written here: a
     * list would need remembering, and forgetting it is precisely the failure —
     * an annotation naming an undeclared permission denies every request to that
     * route, permanently and indistinguishably from a legitimate denial.
     */
    @Test
    void shouldDeclareEveryPermissionConstant() throws IllegalAccessException {
        // Given: the permission constants, which are the ones annotations use
        List<String> constants = permissionConstants();

        // When & Then: the registry declares each
        assertThat(constants).isNotEmpty();
        assertThat(registry.permissions()).extracting(PermissionDefinition::key)
                .containsAll(constants);
    }

    /**
     * Tests that every stored role maps to a role the registry declares.
     *
     * <p>{@code AdminRole} lives in bottin-core and the registry keys live here,
     * so nothing but this test stops the two drifting. A stored role with no
     * registry entry is refused sign-in — fail-closed, but a locked-out
     * administrator with no obvious cause.
     */
    @Test
    void shouldDeclareARoleForEveryStoredAdminRole() {
        // Given: the registry's role keys
        Set<String> declared = registry.roles().stream()
                .map(RoleDefinition::key)
                .collect(java.util.stream.Collectors.toSet());

        // When & Then: every persistable role is among them
        assertThat(Stream.of(AdminRole.values()))
                .allSatisfy(role -> assertThat(declared)
                        .as("registry declares a role for AdminRole.%s", role)
                        .contains(role == AdminRole.READONLY
                                ? AdminPermissions.READONLY
                                : AdminPermissions.ADMIN));
    }

    /**
     * Tests that the default role grants the least authority available.
     *
     * <p>The default is reached only when role assignment has gone wrong, and
     * the safe answer to that is the role that can change nothing.
     */
    @Test
    void shouldDefaultToTheLeastPrivilegedRole() {
        assertThat(registry.defaultRole()).isEqualTo(AdminPermissions.READONLY);

        RoleDefinition defaultRole = registry.roles().stream()
                .filter(role -> role.key().equals(registry.defaultRole()))
                .findFirst()
                .orElseThrow();
        assertThat(defaultRole.permissions()).containsExactly(AdminPermissions.READ);
    }

    /** The {@code admin:} permission constants declared on {@link AdminPermissions}. */
    private static List<String> permissionConstants() throws IllegalAccessException {
        List<String> keys = new java.util.ArrayList<>();
        for (Field field : AdminPermissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String value = (String) field.get(null);
            // Role keys are declared on the same class and are not permissions;
            // the "admin:" prefix is what tells them apart.
            if (value.startsWith("admin:")) {
                keys.add(value);
            }
        }
        return keys;
    }
}
