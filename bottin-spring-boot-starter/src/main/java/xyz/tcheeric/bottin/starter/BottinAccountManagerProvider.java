package xyz.tcheeric.bottin.starter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.tcheeric.nsecbunker.account.registration.AccountManager;
import xyz.tcheeric.nsecbunker.account.registration.spi.AccountManagerProvider;

/**
 * SPI provider that supplies bottin's {@link PersistentAccountManager} to nsecbunker-java.
 *
 * <p>This provider has priority 100, which is higher than the default in-memory
 * implementation (priority 0), ensuring that database-backed persistence is used
 * when bottin is on the classpath.</p>
 */
@Component
@RequiredArgsConstructor
public class BottinAccountManagerProvider implements AccountManagerProvider {

    private final PersistentAccountManager persistentAccountManager;

    @Override
    public AccountManager create() {
        return persistentAccountManager;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String name() {
        return "bottin-persistent";
    }

    @Override
    public boolean isAvailable() {
        return persistentAccountManager != null;
    }
}
