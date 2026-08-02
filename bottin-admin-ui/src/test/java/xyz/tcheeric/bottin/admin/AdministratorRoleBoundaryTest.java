package xyz.tcheeric.bottin.admin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.app.BottinAdminApplication;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.admin.config.RefusedAdminRequestLogFilter;
import xyz.tcheeric.bottin.persistence.repository.AdminUserRepository;
import xyz.tcheeric.bottin.service.AdminUserService;
import xyz.tcheeric.nap.core.SessionRecord;
import xyz.tcheeric.nap.server.SessionStore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that the two roles differ where the decision is made, not merely in
 * which controls a page renders.
 *
 * <p>These cannot be {@code @WebMvcTest} slices. nap's permission interceptor is
 * installed by its auto-configuration, which a slice does not load — so a slice
 * would let every management request through and pass while asserting nothing.
 * The whole application is booted instead, with real sessions carrying the exact
 * permissions the resolver grants each role.
 */
@SpringBootTest(classes = BottinAdminApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "bottin.admin.npub=npub1antwcjptjquv5k2wkh6mkr2gzayzeg046spy97guwu2p9cy2s8ush27znn"
})
class AdministratorRoleBoundaryTest {

    private static final String MASTER_HEX = "ecd6ec482b9038ca594eb5f5bb0d4817482ca1f5d40242f91c771412e08a81f9";

    private static final String ADMIN_NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";
    private static final String ADMIN_HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    private static final String OTHER_NPUB = "npub1sn0wdenkukak0d9dfczzeacvhkrgz92ak56egt7vdgzn8pv2wfqqhrjdv9";

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
    void addOneOrdinaryAdministrator() {
        repository.deleteAll();
        adminUserService.add(ADMIN_NPUB, "Ops laptop", MASTER_HEX);
    }

    /**
     * Tests that an added administrator cannot add another, when asking the
     * deployment directly rather than through a page. A hidden button is not a
     * permission; this is the assertion that makes the boundary real.
     */
    @Test
    void shouldRefuseAnOrdinaryAdministratorAddingAnother() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators")
                        .with(csrf())
                        .cookie(sessionFor(ADMIN_HEX))
                        .param("key", OTHER_NPUB)
                        .param("label", "Smuggled"))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that an added administrator cannot remove anybody, including
     * themselves or the super administrator.
     */
    @Test
    void shouldRefuseAnOrdinaryAdministratorRemovingAnyone() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators/" + ADMIN_HEX + "/remove")
                        .with(csrf())
                        .cookie(sessionFor(ADMIN_HEX)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/settings/administrators/" + MASTER_HEX + "/remove")
                        .with(csrf())
                        .cookie(sessionFor(ADMIN_HEX)))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that an added administrator cannot promote themselves by adding
     * their own key again — the addition path is closed to them entirely.
     */
    @Test
    void shouldRefuseAnOrdinaryAdministratorPromotingThemselves() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators")
                        .with(csrf())
                        .cookie(sessionFor(ADMIN_HEX))
                        .param("key", ADMIN_NPUB))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that the super administrator is allowed through the same endpoint
     * the ordinary administrator was refused, so the refusals above are about
     * the role rather than about the request being malformed.
     */
    @Test
    void shouldAllowTheSuperAdministratorThroughTheSameEndpoint() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators")
                        .with(csrf())
                        .cookie(sessionFor(MASTER_HEX))
                        .param("key", OTHER_NPUB)
                        .param("label", "Second laptop"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Tests that an added administrator sees the administrator list but is
     * offered no controls, and is told why rather than shown a section that
     * silently lacks half its content.
     */
    @Test
    void shouldShowTheListWithoutControlsToAnOrdinaryAdministrator() throws Exception {
        mockMvc.perform(get("/admin/settings").cookie(sessionFor(ADMIN_HEX)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ops laptop")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Add administrator"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Only the super administrator can add or remove administrators.")));
    }

    /**
     * Tests that the super administrator is offered the controls, so the
     * assertion above is about the role and not about the section being absent
     * for everyone.
     */
    @Test
    void shouldOfferTheControlsToTheSuperAdministrator() throws Exception {
        mockMvc.perform(get("/admin/settings").cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add administrator")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Remove")));
    }

    /**
     * Tests that a refused management attempt reaches the security log, naming
     * the administrator who made it.
     *
     * <p>Worth asserting rather than assuming: the permission interceptor writes
     * 403 straight onto the response, so neither the controller nor Spring
     * Security's access-denied handling ever runs. Without a filter watching for
     * the outcome, the most security-relevant refusal the dashboard can produce
     * would leave no trace at all.
     */
    @Test
    void shouldRecordARefusedManagementAttemptAgainstTheAdministratorWhoMadeIt() throws Exception {
        Logger filterLog = (Logger) LoggerFactory.getLogger(RefusedAdminRequestLogFilter.class);
        ListAppender<ILoggingEvent> recorded = new ListAppender<>();
        recorded.start();
        filterLog.addAppender(recorded);

        try {
            mockMvc.perform(post("/admin/settings/administrators")
                            .with(csrf())
                            .cookie(sessionFor(ADMIN_HEX))
                            .param("key", OTHER_NPUB))
                    .andExpect(status().isForbidden());

            assertThat(recorded.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("administrator_change_rejected")
                            .contains("reason=insufficient_permission")
                            .contains(ADMIN_HEX));
        } finally {
            filterLog.detachAppender(recorded);
        }
    }

    /**
     * A real session carrying exactly the permissions the resolver grants that
     * role — the ordinary administrator's deliberately without
     * {@link AdminPermissions#MANAGE_ADMINS}.
     */
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
