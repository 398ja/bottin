package xyz.tcheeric.bottin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Nip05RecordService#mergeWithDefaults(List)} — the
 * app-relay union applied on record creation (Option C). Repos are unused by
 * this method, so the service is constructed with nulls.
 */
class Nip05RecordServiceMergeTest {

    private Nip05RecordService serviceWithDefault(List<String> defaults) {
        Nip05RecordProperties props = new Nip05RecordProperties();
        props.setDefaultRelays(defaults);
        return new Nip05RecordService(null, null, new ObjectMapper(), props);
    }

    @Test
    void emptyCallerRelays_getConfiguredAppDefault() {
        Nip05RecordService svc = serviceWithDefault(List.of("wss://relay.staging.398ja.xyz"));
        assertThat(svc.mergeWithDefaults(List.of()))
                .containsExactly("wss://relay.staging.398ja.xyz");
    }

    @Test
    void nullCallerRelays_getConfiguredAppDefault() {
        Nip05RecordService svc = serviceWithDefault(List.of("wss://relay.staging.398ja.xyz"));
        assertThat(svc.mergeWithDefaults(null))
                .containsExactly("wss://relay.staging.398ja.xyz");
    }

    @Test
    void callerRelays_unionedAppFirst_withDedup() {
        Nip05RecordService svc = serviceWithDefault(List.of("wss://relay.staging.398ja.xyz"));
        // caller sends the (bad) imani.casa plus a duplicate of the app relay
        assertThat(svc.mergeWithDefaults(
                List.of("wss://relay.imani.casa", "wss://relay.staging.398ja.xyz")))
                .containsExactly("wss://relay.staging.398ja.xyz", "wss://relay.imani.casa");
    }

    @Test
    void noDefaultConfigured_isPassthrough() {
        Nip05RecordService svc = serviceWithDefault(List.of());
        assertThat(svc.mergeWithDefaults(List.of("wss://relay.example.com")))
                .containsExactly("wss://relay.example.com");
    }
}
