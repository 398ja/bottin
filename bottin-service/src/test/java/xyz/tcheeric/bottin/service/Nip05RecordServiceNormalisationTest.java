package xyz.tcheeric.bottin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import xyz.tcheeric.bottin.core.exception.Nip05RecordExistsException;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * imani-wallet#342: every handle {@link Nip05RecordService} receives is trimmed and lowercased
 * before it is checked, stored or looked up. Handles are always stored lowercase.
 */
@ExtendWith(MockitoExtension.class)
class Nip05RecordServiceNormalisationTest {

    private static final String PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Mock
    private Nip05RecordRepository recordRepository;

    @Mock
    private DomainRepository domainRepository;

    private Nip05RecordService service;
    private DomainEntity domain;

    @BeforeEach
    void setUp() {
        service = new Nip05RecordService(recordRepository, domainRepository, new ObjectMapper(),
                new Nip05RecordProperties());
        domain = DomainEntity.builder().id(1L).name("example.com").verified(true).build();
    }

    // " alice", "alice ", "Alice" and " ALICE " are each stored as "alice".
    @ParameterizedTest
    @ValueSource(strings = {" alice", "alice ", "Alice", " ALICE "})
    void shouldStoreCanonicalHandleWhenCreatedPaddedOrMixedCase(String raw) {
        // Arrange
        when(domainRepository.findByName("example.com")).thenReturn(Optional.of(domain));
        when(recordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.create(raw, " Example.com ", PUBKEY, List.of());

        // Assert
        ArgumentCaptor<Nip05RecordEntity> saved = ArgumentCaptor.forClass(Nip05RecordEntity.class);
        verify(recordRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");
    }

    // An existing handle is still a conflict when the new claim is padded and mixed-case.
    @Test
    void shouldRefuseExistingHandleClaimedPaddedAndMixedCase() {
        // Arrange
        when(domainRepository.findByName("example.com")).thenReturn(Optional.of(domain));
        when(recordRepository.existsByUsernameAndDomain("alice", domain)).thenReturn(true);

        // Act and assert
        assertThatThrownBy(() -> service.create(" Alice ", "example.com", PUBKEY, List.of()))
                .isInstanceOf(Nip05RecordExistsException.class);
        verify(recordRepository, never()).save(any());
    }

    // Resolve "Alice@Domain " finds alice@domain: both parts are trimmed and lowercased.
    @Test
    void shouldFindRecordByCanonicalNip05() {
        // Arrange
        Nip05RecordEntity alice = Nip05RecordEntity.builder()
                .id(1L).domain(domain).username("alice").pubkey(PUBKEY).relaysJson("[]").enabled(true).build();
        when(recordRepository.findByUsernameAndDomainName("alice", "domain")).thenReturn(Optional.of(alice));

        // Act and assert
        assertThat(service.findByNip05("Alice@Domain ")).isPresent();
    }

    // A domain filter with padding still matches the stored lowercase domain.
    @Test
    void shouldFilterByCanonicalDomain() {
        // Arrange
        when(recordRepository.findByDomainName("example.com", Pageable.unpaged())).thenReturn(Page.empty());

        // Act
        service.findByDomain(" Example.com ", Pageable.unpaged());

        // Assert
        verify(recordRepository).findByDomainName("example.com", Pageable.unpaged());
    }
}
