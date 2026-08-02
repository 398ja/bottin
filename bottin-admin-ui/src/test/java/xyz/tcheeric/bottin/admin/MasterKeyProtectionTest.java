package xyz.tcheeric.bottin.admin;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.app.BottinAdminApplication;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.persistence.repository.AdminUserRepository;
import xyz.tcheeric.bottin.service.AdminUserService;
import xyz.tcheeric.nap.core.SessionRecord;
import xyz.tcheeric.nap.server.SessionStore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that nothing reachable through the interface can leave the deployment
 * with nobody able to sign in.
 *
 * <p>The master key admits an operator when the stored data is empty, wrong, or
 * freshly restored. If it could be removed or demoted, a single save could lock
 * out everybody including the person making it — a state nothing in the product
 * could undo, and the one failure here with no recovery path.
 *
 * <p>Shares its context configuration with {@code AdministratorRoleBoundaryTest}
 * so the two reuse one application context rather than booting two.
 */
@SpringBootTest(classes = BottinAdminApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "bottin.admin.npub=npub1antwcjptjquv5k2wkh6mkr2gzayzeg046spy97guwu2p9cy2s8ush27znn"
})
class MasterKeyProtectionTest {

    private static final String MASTER_HEX = "ecd6ec482b9038ca594eb5f5bb0d4817482ca1f5d40242f91c771412e08a81f9";
    private static final String MASTER_NPUB = "npub1antwcjptjquv5k2wkh6mkr2gzayzeg046spy97guwu2p9cy2s8ush27znn";

    private static final String ADMIN_NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";
    private static final String ADMIN_HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    private static final String SESSION_COOKIE = "admin_session";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private AdminUserRepository repository;

    @BeforeEach
    void startWithNoAddedAdministrators() {
        repository.deleteAll();
    }

    /**
     * Tests that even the super administrator cannot remove the master key, when
     * asking directly rather than through a page that offers no such control.
     */
    @Test
    void shouldRefuseRemovingTheMasterKeyEvenForTheSuperAdministrator() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators/" + MASTER_HEX + "/remove")
                        .with(csrf())
                        .cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        // Still signed in and working afterwards
        mockMvc.perform(get("/admin/dashboard").cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().isOk());
    }

    /**
     * Tests that the master key is protected in whichever encoding the request
     * names it, so its bech32 form cannot slip past a check its hex form fails.
     */
    @Test
    void shouldRefuseRemovingTheMasterKeyGivenAsNpub() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators/" + MASTER_NPUB + "/remove")
                        .with(csrf())
                        .cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    /**
     * Tests that the page offers no control to remove the super administrator,
     * asserted against the rendered markup rather than by eye.
     */
    @Test
    void shouldOfferNoRemoveControlForTheSuperAdministrator() throws Exception {
        adminUserService.add(ADMIN_NPUB, "Ops laptop", MASTER_HEX);

        mockMvc.perform(get("/admin/settings").cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().isOk())
                // The added administrator has a remove form...
                .andExpect(content().string(containsString(
                        "/admin/settings/administrators/" + ADMIN_HEX + "/remove")))
                // ...and the master key does not.
                .andExpect(content().string(not(containsString(
                        "/admin/settings/administrators/" + MASTER_HEX + "/remove"))))
                .andExpect(content().string(containsString("Cannot be removed here")));
    }

    /**
     * Tests the lock-out scenario in full: every added administrator removed,
     * and the master key still gets in.
     */
    @Test
    void shouldKeepTheMasterKeyWorkingAfterRemovingEveryAdministrator() throws Exception {
        adminUserService.add(ADMIN_NPUB, "Ops laptop", MASTER_HEX);
        adminUserService.remove(ADMIN_HEX, MASTER_HEX);
        assertThat(repository.count()).isZero();

        mockMvc.perform(get("/admin/dashboard").cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/settings").cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().isOk());
    }

    private Cookie sessionFor(String pubkeyHex) {
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();

        boolean isMaster = MASTER_HEX.equals(pubkeyHex);
        List<String> roles = List.of(isMaster ? AdminPermissions.SUPER_ADMIN : AdminPermissions.ADMIN);
        List<String> permissions = isMaster
                ? List.of(AdminPermissions.READ, AdminPermissions.WRITE, AdminPermissions.MANAGE_ADMINS)
                : List.of(AdminPermissions.READ, AdminPermissions.WRITE);

        sessionStore.createForChallenge(SessionRecord.create(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                accessToken,
                pubkeyHex,
                pubkeyHex,
                roles,
                permissions,
                now,
                now + 3600));

        return new Cookie(SESSION_COOKIE, accessToken);
    }
}
