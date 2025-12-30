package xyz.tcheeric.bottin.starter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.tcheeric.nsecbunker.account.nip05.Nip05Manager;
import xyz.tcheeric.nsecbunker.account.nip05.spi.Nip05ManagerProvider;

/**
 * SPI provider that supplies bottin's {@link PersistentNip05Manager} to nsecbunker-java.
 *
 * <p>This provider has priority 100, which is higher than the default in-memory
 * implementation (priority 0), ensuring that database-backed persistence is used
 * when bottin is on the classpath.</p>
 */
@Component
@RequiredArgsConstructor
public class BottinNip05ManagerProvider implements Nip05ManagerProvider {

    private final PersistentNip05Manager persistentNip05Manager;

    @Override
    public Nip05Manager create() {
        return persistentNip05Manager;
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
        return persistentNip05Manager != null;
    }
}
