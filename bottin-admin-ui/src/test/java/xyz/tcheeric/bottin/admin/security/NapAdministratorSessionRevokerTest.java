package xyz.tcheeric.bottin.admin.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.nap.server.SessionStore;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NapAdministratorSessionRevoker.
 *
 * <p>Thin as this adapter is, it is where removal can fail silently: the session
 * store matches sessions on the hex principal, so handing it anything else
 * revokes nothing and still reports success.
 */
@ExtendWith(MockitoExtension.class)
class NapAdministratorSessionRevokerTest {

    private static final String HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    @Mock
    private SessionStore sessionStore;

    /**
     * Tests that the key is passed through to the session store unchanged, in
     * the hex form the store matches on.
     */
    @Test
    void shouldRevokeByTheHexPrincipal() {
        when(sessionStore.revokeByPrincipal(eq(HEX), anyLong())).thenReturn(1);

        new NapAdministratorSessionRevoker(sessionStore).revokeSessionsFor(HEX);

        verify(sessionStore).revokeByPrincipal(eq(HEX), anyLong());
    }

    /**
     * Tests that the number of sessions ended is reported back, so a removal
     * that ended nothing can be logged and noticed.
     */
    @Test
    void shouldReturnHowManySessionsWereEnded() {
        when(sessionStore.revokeByPrincipal(eq(HEX), anyLong())).thenReturn(3);

        int revoked = new NapAdministratorSessionRevoker(sessionStore).revokeSessionsFor(HEX);

        assertThat(revoked).isEqualTo(3);
    }

    /**
     * Tests that the revocation timestamp is in epoch seconds.
     *
     * <p>NAP works in seconds throughout — its challenge and session responses
     * carry second-precision timestamps. Passing milliseconds would place the
     * revocation tens of thousands of years in the future, which a store
     * comparing it against "now" could read as not yet revoked.
     */
    @Test
    void shouldRecordRevocationTimeInEpochSeconds() {
        long before = Instant.now().getEpochSecond();
        when(sessionStore.revokeByPrincipal(eq(HEX), anyLong())).thenReturn(0);

        new NapAdministratorSessionRevoker(sessionStore).revokeSessionsFor(HEX);

        ArgumentCaptor<Long> revokedAt = ArgumentCaptor.forClass(Long.class);
        verify(sessionStore).revokeByPrincipal(eq(HEX), revokedAt.capture());
        assertThat(revokedAt.getValue())
                .isBetween(before, Instant.now().getEpochSecond());
    }
}
