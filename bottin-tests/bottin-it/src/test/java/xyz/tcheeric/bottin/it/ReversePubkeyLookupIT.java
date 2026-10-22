package xyz.tcheeric.bottin.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;
import xyz.tcheeric.bottin.service.Nip05RecordService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reviewer IT (imani-gateway-core#173 / bottin#42): the reverse lookup on real PostgreSQL.
 */
@Transactional
class ReversePubkeyLookupIT extends BaseIntegrationTest {

    // fiatjaf: npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6
    private static final String KEY = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";
    private static final String NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";
    private static final String OTHER = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Autowired private Nip05RecordRepository repo;
    @Autowired private DomainRepository domains;
    @Autowired private Nip05RecordService service;
    @Autowired private JdbcTemplate jdbc;

    private DomainEntity a;
    private DomainEntity b;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        domains.deleteAll();
        a = domains.save(DomainEntity.builder().name("a.example").verified(true).build());
        b = domains.save(DomainEntity.builder().name("b.example").verified(true).build());
    }

    private Nip05RecordEntity rec(DomainEntity d, String user, String key, Instant created, boolean enabled) {
        return repo.saveAndFlush(Nip05RecordEntity.builder().domain(d).username(user).pubkey(key)
                .relaysJson("[]").enabled(enabled).createdAt(created).build());
    }

    @Test
    void oldestEnabledWinsAcrossDomainsAndDisabledIsNeverAnswered() {
        rec(a, "zzz", KEY, Instant.parse("2026-01-03T00:00:00Z"), true);
        rec(b, "aaa", KEY, Instant.parse("2026-01-02T00:00:00Z"), true);
        rec(a, "old", KEY, Instant.parse("2026-01-01T00:00:00Z"), false);
        rec(a, "noise", OTHER, Instant.parse("2025-01-01T00:00:00Z"), true);

        List<Nip05RecordEntity> all = repo.findEnabledByPubkeyOldestFirst(KEY, PageRequest.of(0, 10));
        assertThat(all).extracting(Nip05RecordEntity::getNip05).containsExactly("aaa@b.example", "zzz@a.example");
        assertThat(service.findByPubkey(KEY)).map(Nip05RecordData::getNip05).contains("aaa@b.example");
    }

    @Test
    void equalCreatedAtIsBrokenByIdDeterministically() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        Nip05RecordEntity first = rec(b, "zzz", KEY, t, true);
        rec(a, "aaa", KEY, t, true);
        for (int i = 0; i < 5; i++) {
            assertThat(service.findByPubkey(KEY)).map(Nip05RecordData::getId).contains(first.getId());
        }
    }

    @Test
    void onlyDisabledRecordsGiveEmpty() {
        rec(a, "gone", KEY, Instant.parse("2026-01-01T00:00:00Z"), false);
        assertThat(service.findByPubkey(KEY)).isEmpty();
    }

    @Test
    void caseAndNpubAreCanonicalised() {
        rec(a, "alice", KEY, Instant.parse("2026-01-01T00:00:00Z"), true);
        assertThat(service.findByPubkey(KEY.toUpperCase())).isPresent();
        assertThat(service.findByPubkey("  " + KEY + "\t")).isPresent();
        assertThat(service.findByPubkey(NPUB)).map(Nip05RecordData::getNip05).contains("alice@a.example");
        assertThat(service.findByPubkey(NPUB.toUpperCase())).isPresent();
        assertThatThrownBy(() -> service.findByPubkey(NPUB.substring(0, NPUB.length() - 1) + "q"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findByPubkey(KEY.substring(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A legacy upper-case stored key (pre-lowercasing writes) is NOT found: documents the exact-match. */
    @Test
    void storedKeyMatchIsExact() {
        jdbc.update("INSERT INTO nip05_records (domain_id, username, pubkey, enabled) VALUES (?, 'legacy', ?, true)",
                a.getId(), KEY.toUpperCase());
        assertThat(service.findByPubkey(KEY)).isEmpty();
    }

    @Test
    void postgresPlanUsesThePubkeyIndex() {
        rec(a, "alice", KEY, Instant.parse("2026-01-01T00:00:00Z"), true);
        jdbc.execute("SET LOCAL enable_seqscan = off");
        List<String> plan = jdbc.queryForList(
                "EXPLAIN SELECT r.* FROM nip05_records r JOIN domains d ON d.id = r.domain_id "
                        + "WHERE r.pubkey = '" + KEY + "' AND r.enabled = true ORDER BY r.created_at, r.id LIMIT 1",
                String.class);
        System.out.println("REVIEW_PLAN\n" + String.join("\n", plan));
        assertThat(String.join("\n", plan)).contains("idx_nip05_records_pubkey");
        Optional<String> count = Optional.ofNullable(jdbc.queryForObject("SELECT count(*) FROM pg_indexes "
                + "WHERE indexname = 'idx_nip05_records_pubkey'", Long.class)).map(String::valueOf);
        assertThat(count).contains("1");
    }
}
