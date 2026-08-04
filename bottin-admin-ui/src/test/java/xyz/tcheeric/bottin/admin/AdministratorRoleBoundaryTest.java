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
import xyz.tcheeric.bottin.admin.config.AdminPermissionRegistryConfig;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.nap.server.acl.PermissionRegistry;
import xyz.tcheeric.nap.server.acl.RoleDefinition;
import xyz.tcheeric.bottin.admin.config.RefusedAdminRequestLogFilter;
import xyz.tcheeric.bottin.core.model.AdminRole;
import xyz.tcheeric.bottin.core.nostr.NostrPublicKeys;
import xyz.tcheeric.bottin.persistence.entity.AdminUserEntity;
import xyz.tcheeric.bottin.persistence.repository.AdminUserRepository;
import xyz.tcheeric.bottin.service.AdminUserService;
import xyz.tcheeric.nap.core.SessionRecord;
import xyz.tcheeric.nap.core.SessionStore;

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

    /**
     * The same key as {@link #OTHER_NPUB}, in canonical hex. Decoded rather than
     * written out, because a hand-copied hex that is not that npub would store
     * an administrator nobody signs in as, and the test would pass by admitting
     * nothing rather than by refusing correctly.
     */
    private static final String OTHER_HEX = NostrPublicKeys.toCanonicalHex(OTHER_NPUB).orElseThrow();

    private static final String SESSION_COOKIE = "admin_session";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private AdminUserRepository repository;

    @Autowired
    private xyz.tcheeric.bottin.service.DomainService domainService;

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
        boolean isMaster = MASTER_HEX.equals(pubkeyHex);
        return sessionForRole(pubkeyHex,
                isMaster ? AdminPermissions.SUPER_ADMIN : AdminPermissions.ADMIN);
    }

    private Cookie sessionForRole(String pubkeyHex, String roleKey) {
        // The cookie carries the session id: NapSessionFilter resolves the
        // session with getBySessionId, not by access token.
        String sessionId = UUID.randomUUID().toString();
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();

        List<String> roles = List.of(roleKey);
        List<String> permissions = permissionsOf(roleKey);

        sessionStore.createForChallenge(SessionRecord.create(
                sessionId,
                UUID.randomUUID().toString(),
                accessToken,
                pubkeyHex,
                pubkeyHex,
                roles,
                permissions,
                now,
                now + 3600));

        return new Cookie(SESSION_COOKIE, sessionId);
    }

    /**
     * Tests that a session without {@code admin:settings-write} is refused the
     * settings write, by the interceptor rather than by the page.
     *
     * <p>Booted rather than sliced deliberately: a {@code @WebMvcTest} does not
     * load nap's auto-configuration, so it would admit this request and pass
     * while asserting nothing about the permission.
     */
    @Test
    void shouldRefuseTheSettingsWriteToASessionWithoutSettingsWrite() throws Exception {
        // Given: an administrator stored as read-only
        Cookie readonly = readonlyAdministratorSession();

        // When: it submits the settings form
        // Then: the interceptor refuses it
        mockMvc.perform(post("/admin/settings").cookie(readonly).with(csrf())
                        .param("rateLimitPerMinute", "60"))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that a read-only session can still see the settings page.
     *
     * <p>Withholding the write must not withhold the read: a role that cannot be
     * used to look at anything is not a read-only role, and the failure would be
     * invisible if only the refusal above were asserted.
     */
    @Test
    void shouldStillAllowAReadonlySessionToViewSettings() throws Exception {
        Cookie readonly = readonlyAdministratorSession();

        mockMvc.perform(get("/admin/settings").cookie(readonly))
                .andExpect(status().isOk());
    }

    /**
     * A session for an administrator stored as {@code READONLY}.
     *
     * <p>The row is written directly because no page offers the role, and
     * because the row is what decides: {@code NapSessionFilter} re-resolves the
     * ACL on every request and replaces the session's permissions with the
     * resolver's answer. A session record built here with read-only permissions
     * would be silently upgraded to whatever the stored role grants — which is
     * exactly what made an earlier version of this test pass while asserting
     * nothing.
     */
    private Cookie readonlyAdministratorSession() {
        repository.save(AdminUserEntity.builder()
                .pubkey(OTHER_HEX)
                .label("Read-only")
                .role(AdminRole.READONLY)
                .enabled(true)
                .addedByPubkey(MASTER_HEX)
                .createdAt(Instant.now())
                .build());
        return sessionForRole(OTHER_HEX, AdminPermissions.READONLY);
    }

    /**
     * Tests that an added administrator cannot change deployment settings.
     *
     * <p>This is the assertion that gives {@code admin:settings-write} a reason
     * to exist. The administrator seeded by {@code @BeforeEach} holds
     * {@code admin:write} and writes records freely; the settings write is
     * refused because it is the one capability their role withholds. Revert the
     * annotation on {@code saveSettings} to {@code admin:write} and this fails.
     */
    @Test
    void shouldRefuseTheSettingsWriteToAnAddedAdministrator() throws Exception {
        mockMvc.perform(post("/admin/settings").cookie(sessionFor(ADMIN_HEX)).with(csrf())
                        .param("rateLimitPerMinute", "60"))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that the super administrator is allowed the same write, so the
     * refusal above is about the role rather than about the request.
     *
     * <p>Asserting "not forbidden" rather than a status: the point is the
     * permission boundary, and the form's own validation decides the rest.
     */
    @Test
    void shouldAllowTheSuperAdministratorToWriteSettings() throws Exception {
        mockMvc.perform(post("/admin/settings").cookie(sessionFor(MASTER_HEX)).with(csrf())
                        .param("rateLimitPerMinute", "60"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("the super administrator holds admin:settings-write")
                        .isNotEqualTo(403));
    }

    /**
     * Tests that an added administrator cannot add a domain.
     *
     * <p>This is the assertion that gives {@code admin:manage-domains} a reason
     * to exist. The administrator seeded by {@code @BeforeEach} holds
     * {@code admin:write} and writes records freely; adding a domain is refused
     * because it commits the deployment to answering for a name. Revert the
     * annotation on {@code createDomain} to {@code admin:write} and this fails.
     */
    @Test
    void shouldRefuseAnAddedAdministratorAddingADomain() throws Exception {
        mockMvc.perform(post("/admin/domains").cookie(sessionFor(ADMIN_HEX)).with(csrf())
                        .param("name", "smuggled.test"))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that an added administrator cannot delete a domain either.
     *
     * <p>Asserted separately from creation because the pair is the point: an
     * administrator who could delete a domain but not recreate it would hold a
     * capability destructive in one direction only.
     */
    @Test
    void shouldRefuseAnAddedAdministratorDeletingADomain() throws Exception {
        mockMvc.perform(post("/admin/domains/1/delete").cookie(sessionFor(ADMIN_HEX)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that both verification steps are refused an added administrator,
     * since verifying a name is operating a domain they could not have added.
     */
    @Test
    void shouldRefuseAnAddedAdministratorDrivingDomainVerification() throws Exception {
        mockMvc.perform(post("/admin/domains/1/verify").cookie(sessionFor(ADMIN_HEX)).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/domains/1/verify/attempt").cookie(sessionFor(ADMIN_HEX)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that the super administrator reaches the same route the added
     * administrator was refused, so the refusals above are about the role rather
     * than about the request being malformed or the route being absent.
     *
     * <p>Asserting "not forbidden" rather than a status: the point is the
     * permission boundary, and the form's own validation decides the rest.
     */
    @Test
    void shouldAllowTheSuperAdministratorToAddADomain() throws Exception {
        mockMvc.perform(post("/admin/domains").cookie(sessionFor(MASTER_HEX)).with(csrf())
                        .param("name", "permitted.test"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("the super administrator holds admin:manage-domains")
                        .isNotEqualTo(403));
    }

    /**
     * Tests that viewing a domain does not issue a verification token for an
     * administrator who may not drive verification.
     *
     * <p>Asserted on the stored token rather than on the response, because the
     * route answers 200 either way — the whole point is a side effect on a GET.
     * {@code viewDomain} auto-initiates verification for an unverified domain,
     * so before this guard an added administrator could obtain by loading a page
     * exactly what {@code POST /verify} refuses them, and the new permission
     * would bind only those who used the button.
     */
    @Test
    void shouldNotIssueAVerificationTokenForAnAdministratorWhoMayNotVerify() throws Exception {
        // Given: a domain nobody has verified yet
        var domain = domainService.create("read-only-view.test", MASTER_HEX);
        assertThat(domain.getVerificationToken()).isNull();

        // When: an added administrator opens it
        mockMvc.perform(get("/admin/domains/" + domain.getId()).cookie(sessionFor(ADMIN_HEX)))
                .andExpect(status().isOk());

        // Then: no token was minted on their behalf
        assertThat(domainService.findById(domain.getId()).orElseThrow().getVerificationToken())
                .as("viewing must not initiate verification for a read-only administrator")
                .isNull();
    }

    /**
     * Tests the same view as the super administrator, so the assertion above is
     * about the role rather than about verification having quietly stopped
     * working for everybody.
     */
    @Test
    void shouldIssueAVerificationTokenForTheSuperAdministrator() throws Exception {
        // Given: a domain nobody has verified yet
        var domain = domainService.create("super-admin-view.test", MASTER_HEX);

        // When: the super administrator opens it
        mockMvc.perform(get("/admin/domains/" + domain.getId()).cookie(sessionFor(MASTER_HEX)))
                .andExpect(status().isOk());

        // Then: verification was initiated as before
        assertThat(domainService.findById(domain.getId()).orElseThrow().getVerificationToken())
                .as("the super administrator still gets the token the page instructs them to publish")
                .isNotNull();
    }

    /**
     * Tests that an added administrator can still see the domain list.
     *
     * <p>Withholding the change must not withhold the read: the records page
     * asks them to pick a domain, which they cannot do if the list is refused.
     */
    @Test
    void shouldStillAllowAnAddedAdministratorToViewDomains() throws Exception {
        mockMvc.perform(get("/admin/domains").cookie(sessionFor(ADMIN_HEX)))
                .andExpect(status().isOk());
    }

    /**
     * The permissions the deployment's registry grants a role.
     *
     * <p>Read from the registry rather than listed here, so this test cannot
     * assert a permission set the application does not actually issue — which is
     * how a session in a test can pass a route the same session would fail in
     * production.
     */
    private static List<String> permissionsOf(String roleKey) {
        PermissionRegistry registry = new AdminPermissionRegistryConfig().adminPermissionRegistry();
        return registry.roles().stream()
                .filter(role -> role.key().equals(roleKey))
                .findFirst()
                .map(RoleDefinition::permissions)
                .map(List::copyOf)
                .orElseThrow(() -> new IllegalStateException("registry declares no role " + roleKey));
    }

}
