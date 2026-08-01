package xyz.tcheeric.bottin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bottin.core.exception.SettingsNotFoundException;
import xyz.tcheeric.bottin.core.model.SettingsData;
import xyz.tcheeric.bottin.persistence.entity.SettingsEntity;
import xyz.tcheeric.bottin.persistence.repository.SettingsRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SettingsService.
 *
 * <p>Covers the two responsibilities the service owns that nothing else can:
 * translating between the stored relay JSON and the domain lists, and refusing
 * to store a relay URL no client could ever connect to.
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    private static final String EMPTY_JSON_ARRAY = "[]";

    @Mock
    private SettingsRepository settingsRepository;

    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new SettingsService(settingsRepository, new ObjectMapper());
    }

    /**
     * Tests that both stored relay lists are parsed back out of their JSON
     * columns, so a caller never handles JSON itself.
     */
    @Test
    void shouldReadBothRelayListsWhenStoredAsJson() {
        // Given: a settings row holding two relays in each list
        when(settingsRepository.findById(SettingsEntity.SINGLETON_ID))
                .thenReturn(Optional.of(storedSettings(
                        "[\"ws://relay-a:7777\",\"wss://relay-b.example\"]",
                        "[\"wss://relay.damus.io\",\"wss://nos.lol\"]")));

        // When: reading the settings
        SettingsData settings = settingsService.find();

        // Then: both lists come back in order
        assertThat(settings.getDefaultRelays())
                .containsExactly("ws://relay-a:7777", "wss://relay-b.example");
        assertThat(settings.getDiscoveryRelays())
                .containsExactly("wss://relay.damus.io", "wss://nos.lol");
    }

    /**
     * Tests that an unconfigured deployment yields empty lists rather than null,
     * so callers never branch on absence.
     */
    @Test
    void shouldReturnEmptyRelayListsWhenNothingIsConfigured() {
        // Given: a freshly migrated settings row
        when(settingsRepository.findById(SettingsEntity.SINGLETON_ID))
                .thenReturn(Optional.of(storedSettings(EMPTY_JSON_ARRAY, EMPTY_JSON_ARRAY)));

        // When: reading the settings
        SettingsData settings = settingsService.find();

        // Then: both lists are empty, not null
        assertThat(settings.getDefaultRelays()).isEmpty();
        assertThat(settings.getDiscoveryRelays()).isEmpty();
    }

    /**
     * Tests that a missing settings row raises rather than being papered over
     * with invented defaults, which would restore configuration from two sources.
     */
    @Test
    void shouldThrowWhenSettingsRowIsAbsent() {
        // Given: no settings row, which a correct migration makes impossible
        when(settingsRepository.findById(SettingsEntity.SINGLETON_ID))
                .thenReturn(Optional.empty());

        // When & Then: reading raises rather than returning a default
        assertThatThrownBy(() -> settingsService.find())
                .isInstanceOf(SettingsNotFoundException.class);
    }

    /**
     * Tests that relay lists are written back as JSON arrays.
     */
    @Test
    void shouldStoreRelayListsAsJsonWhenUpdating() {
        // Given: an existing settings row
        givenStoredSettings();

        // When: saving two system relays
        settingsService.update(settingsWithDefaultRelays(
                List.of("ws://relay-a:7777", "wss://relay-b.example")));

        // Then: they are persisted as a JSON array in order
        assertThat(savedEntity().getDefaultRelaysJson())
                .isEqualTo("[\"ws://relay-a:7777\",\"wss://relay-b.example\"]");
    }

    /**
     * Tests that a relay URL no Nostr client could connect to is rejected, and
     * that the rejection names the offending URL so an operator can find it.
     */
    @Test
    void shouldRejectRelayUrlWhenSchemeIsNotWebsocket() {
        // Given: a submission mixing a valid relay with an http:// one

        // When & Then: the http:// relay is refused, naming the URL
        assertThatThrownBy(() -> settingsService.update(settingsWithDefaultRelays(
                List.of("wss://relay-a.example", "http://relay-b.example"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http://relay-b.example");
    }

    /**
     * Tests that nothing is written when a relay URL is rejected, so a bad
     * submission cannot partially apply.
     */
    @Test
    void shouldNotPersistAnythingWhenRelayUrlIsRejected() {
        // Given: a submission carrying an unusable relay scheme

        // When: an invalid relay is submitted
        assertThatThrownBy(() -> settingsService.update(
                settingsWithDefaultRelays(List.of("http://relay.example"))))
                .isInstanceOf(IllegalArgumentException.class);

        // Then: the row is untouched
        verify(settingsRepository, never()).save(any());
    }

    /**
     * Tests that a relay listed twice is stored once, keeping the order the
     * operator typed.
     */
    @Test
    void shouldCollapseDuplicateRelaysWhenPreservingOrder() {
        // Given: an existing settings row
        givenStoredSettings();

        // When: the same relay is submitted twice among others
        settingsService.update(settingsWithDefaultRelays(
                List.of("wss://b.example", "wss://a.example", "wss://b.example")));

        // Then: the duplicate is collapsed and first-seen order is kept
        assertThat(savedEntity().getDefaultRelaysJson())
                .isEqualTo("[\"wss://b.example\",\"wss://a.example\"]");
    }

    /**
     * Tests that blank entries, which a textarea produces from stray newlines,
     * are dropped rather than stored as empty relay URLs.
     */
    @Test
    void shouldDropBlankEntriesWhenNormalisingRelays() {
        // Given: an existing settings row
        givenStoredSettings();

        // When: the submitted list carries blank and whitespace-only entries
        settingsService.update(settingsWithDefaultRelays(
                List.of("", "  wss://a.example  ", "   ")));

        // Then: only the real relay survives, trimmed
        assertThat(savedEntity().getDefaultRelaysJson())
                .isEqualTo("[\"wss://a.example\"]");
    }

    /**
     * Tests that a blank media server is stored as null, so "unconfigured" has
     * one representation instead of two.
     */
    @Test
    void shouldStoreBlankMediaServerAsNull() {
        // Given: an existing settings row
        givenStoredSettings();

        // When: the media server is submitted blank
        settingsService.update(SettingsData.builder()
                .blossomUrl("   ")
                .rateLimitPerMinute(30)
                .build());

        // Then: null is stored rather than an empty string
        assertThat(savedEntity().getBlossomUrl()).isNull();
    }

    /**
     * Tests that an update writes to the one row the table is allowed to hold
     * rather than inserting a second.
     */
    @Test
    void shouldWriteToTheSingletonRowWhenUpdating() {
        // Given: an existing settings row
        givenStoredSettings();

        // When: saving any change
        settingsService.update(settingsWithDefaultRelays(List.of("wss://a.example")));

        // Then: the persisted entity is the singleton
        assertThat(savedEntity().getId()).isEqualTo(SettingsEntity.SINGLETON_ID);
    }

    private void givenStoredSettings() {
        SettingsEntity stored = storedSettings(EMPTY_JSON_ARRAY, EMPTY_JSON_ARRAY);
        when(settingsRepository.findById(SettingsEntity.SINGLETON_ID)).thenReturn(Optional.of(stored));
        when(settingsRepository.save(any(SettingsEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private SettingsEntity savedEntity() {
        ArgumentCaptor<SettingsEntity> captor = ArgumentCaptor.forClass(SettingsEntity.class);
        verify(settingsRepository).save(captor.capture());
        return captor.getValue();
    }

    private SettingsEntity storedSettings(String defaultRelaysJson, String discoveryRelaysJson) {
        return SettingsEntity.builder()
                .id(SettingsEntity.SINGLETON_ID)
                .defaultRelaysJson(defaultRelaysJson)
                .discoveryRelaysJson(discoveryRelaysJson)
                .rateLimitPerMinute(30)
                .updatedAt(Instant.now())
                .build();
    }

    private SettingsData settingsWithDefaultRelays(List<String> defaultRelays) {
        return SettingsData.builder()
                .blossomUrl("https://blossom.example")
                .defaultRelays(defaultRelays)
                .rateLimitPerMinute(30)
                .build();
    }
}
