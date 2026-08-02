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
import xyz.tcheeric.nap.core.SessionStore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of an administrator's life: added, signed in, removed.
 *
 * <p>Everything else about removal is asserted against mocks, which can only
 * prove that the revoker was <em>called</em>. This proves the effect: a session
 * that loads an admin page stops loading it. That difference is where this
 * feature's risk lives, so this test boots the real application, uses the real
 * session store, and goes through the real security chain.
 *
 * <p><b>Why the ACL refresh interval is an hour here.</b> nap caches each
 * session's authorization decision for that interval. Had removal been left to
 * "the resolver will refuse next time", this test would still pass with a short
 * interval and fail for a real operator who removed somebody and watched them
 * keep working. An hour makes the cached decision permanently stale for the
 * duration of the test, so the second request can only be refused if the session
 * itself was ended.
 *
 * <p><b>Why this is named {@code *Test} and not {@code *IT}.</b> This module
 * binds no failsafe execution, so an {@code *IT} would be silently skipped — the
 * one outcome worse than failing for the requirement it guards. It runs in the
 * ordinary build instead, at the cost of booting a context.
 */
@SpringBootTest(classes = BottinAdminApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "bottin.admin.npub=npub1antwcjptjquv5k2wkh6mkr2gzayzeg046spy97guwu2p9cy2s8ush27znn",
        "nap.acl-refresh-interval-seconds=3600"
})
class AdministratorLifecycleTest {

    private static final String MASTER_HEX = "ecd6ec482b9038ca594eb5f5bb0d4817482ca1f5d40242f91c771412e08a81f9";

    /** The colleague being granted, then denied, access. */
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
    void clearAdministrators() {
        repository.deleteAll();
    }

    /**
     * Tests the whole point of the feature: an added administrator reaches the
     * dashboard, and the moment they are removed their existing session stops
     * working — on the very next request, not at expiry.
     */
    @Test
    void shouldEndALiveSessionOnTheNextRequestAfterRemoval() throws Exception {
        // Given: an added administrator who is signed in and working
        adminUserService.add(ADMIN_NPUB, "Ops laptop", MASTER_HEX);
        Cookie session = signedInSessionFor(ADMIN_HEX);

        mockMvc.perform(get("/admin/dashboard").cookie(session))
                .andExpect(status().isOk());

        // When: the super administrator removes them
        adminUserService.remove(ADMIN_HEX, MASTER_HEX);

        // Then: their very next request is refused
        mockMvc.perform(get("/admin/dashboard").cookie(session))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Tests that a removed administrator is refused even holding a session that
     * was never revoked, so removal does not depend on catching every session.
     *
     * <p>Answered {@code 403} rather than a redirect, and the difference is
     * meaningful: here the session is intact and it is the <em>key</em> that is
     * no longer authorised, whereas a revoked session leaves the caller
     * unauthenticated and sent to sign in. Both refuse; they say different true
     * things. This path is what protects a session the revoker could not reach —
     * one held by another instance, say.
     */
    @Test
    void shouldRefuseARemovedAdministratorHoldingAnUnrevokedSession() throws Exception {
        adminUserService.add(ADMIN_NPUB, "Ops laptop", MASTER_HEX);
        adminUserService.remove(ADMIN_HEX, MASTER_HEX);

        // A session minted after removal: valid, but for a key that no longer administers
        Cookie session = signedInSessionFor(ADMIN_HEX);

        mockMvc.perform(get("/admin/dashboard").cookie(session))
                .andExpect(status().isForbidden());
    }

    /**
     * Tests that removing one administrator leaves the others working. A
     * revocation that matched too broadly would sign everybody out.
     */
    @Test
    void shouldLeaveOtherAdministratorsSignedIn() throws Exception {
        adminUserService.add(ADMIN_NPUB, "Ops laptop", MASTER_HEX);
        Cookie theirs = signedInSessionFor(ADMIN_HEX);
        Cookie masters = signedInSessionFor(MASTER_HEX);

        adminUserService.remove(ADMIN_HEX, MASTER_HEX);

        mockMvc.perform(get("/admin/dashboard").cookie(theirs))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/dashboard").cookie(masters))
                .andExpect(status().isOk());
    }

    /**
     * Tests that the master key holder keeps working with the administrator list
     * empty, so no sequence of removals can lock everybody out.
     */
    @Test
    void shouldKeepAdmittingTheMasterKeyWithAnEmptyAdministratorList() throws Exception {
        assertThat(repository.count()).isZero();

        mockMvc.perform(get("/admin/dashboard").cookie(signedInSessionFor(MASTER_HEX)))
                .andExpect(status().isOk());
    }

    /**
     * A real session in the real store, as completing the handshake would leave
     * behind.
     *
     * <p>The signing half of sign-in is not repeated here — it is feature 005's
     * and is covered there. What matters for removal is that a session exists in
     * the store the filter reads and the revoker writes to.
     */
    private Cookie signedInSessionFor(String pubkeyHex) {
        // The cookie carries the session id: NapSessionFilter resolves the
        // session with getBySessionId, not by access token.
        String sessionId = UUID.randomUUID().toString();
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();

        boolean isMaster = MASTER_HEX.equals(pubkeyHex);
        List<String> roles = List.of(isMaster ? AdminPermissions.SUPER_ADMIN : AdminPermissions.ADMIN);
        List<String> permissions = isMaster
                ? List.of(AdminPermissions.READ, AdminPermissions.WRITE, AdminPermissions.MANAGE_ADMINS)
                : List.of(AdminPermissions.READ, AdminPermissions.WRITE);

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
}
