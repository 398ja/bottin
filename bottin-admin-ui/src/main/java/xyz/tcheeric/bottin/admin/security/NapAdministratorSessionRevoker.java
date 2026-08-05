package xyz.tcheeric.bottin.admin.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.tcheeric.bottin.service.port.AdministratorSessionRevoker;
import xyz.tcheeric.nap.core.SessionStore;

import java.time.Instant;

/**
 * Ends an administrator's sessions through NAP's session store.
 *
 * <p>The store matches sessions on the <em>hex</em> principal. Handing it a
 * bech32 key would revoke nothing and still report success, so the port's
 * contract requires canonical hex and this adapter passes it through unchanged.
 *
 * <p>ponytail: revocation reaches the sessions this instance holds, because the
 * session store is in memory. A deployment running several dashboard instances
 * behind a load balancer would leave a removed administrator working on the
 * instances that did not process the removal, until their session expires. The
 * shipped deployment runs one admin instance, so removal is immediate as
 * specified; a shared session store is the upgrade path if that changes, and
 * {@code sessions_revoked} in the removal log is how the gap would show.
 */
@Component
@RequiredArgsConstructor
public class NapAdministratorSessionRevoker implements AdministratorSessionRevoker {

    private final SessionStore sessionStore;

    @Override
    public int revokeSessionsFor(String pubkeyHex) {
        return sessionStore.revokeByPrincipal(pubkeyHex, Instant.now().getEpochSecond());
    }
}
