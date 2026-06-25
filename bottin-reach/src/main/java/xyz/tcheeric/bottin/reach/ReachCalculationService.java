package xyz.tcheeric.bottin.reach;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.tcheeric.bottin.persistence.entity.ProfileReachEntity;
import xyz.tcheeric.bottin.persistence.entity.ReachCalculationRunEntity;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;
import xyz.tcheeric.bottin.persistence.repository.ProfileReachRepository;
import xyz.tcheeric.bottin.persistence.repository.ReachCalculationRunRepository;
import xyz.tcheeric.bottin.reach.relay.FollowerGatherer;
import xyz.tcheeric.bottin.reach.relay.GatherResult;
import xyz.tcheeric.bottin.reach.relay.Nip65RelayResolver;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes reach for every tracked profile and persists the results.
 *
 * <p>Tracked profiles are the distinct enabled pubkeys in the NIP-05 registry.
 * For each, follower data is gathered from the default application relays plus
 * the profile's NIP-65 relays. A profile whose gather returned no data at all is
 * skipped so its previously stored figure is retained.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReachCalculationService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final Nip05RecordRepository nip05RecordRepository;
    private final ProfileReachRepository profileReachRepository;
    private final ReachCalculationRunRepository runRepository;
    private final FollowerGatherer followerGatherer;
    private final Nip65RelayResolver nip65RelayResolver;
    private final ReachProperties properties;

    /**
     * Runs a full calculation pass over all tracked profiles.
     */
    public void calculateAll() {
        List<String> pubkeys = nip05RecordRepository.findDistinctEnabledPubkeys();
        int maxProfiles = properties.getMaxProfilesPerRun();
        if (pubkeys.size() > maxProfiles) {
            log.warn("reach_run_truncated total={} max={} dropped={}",
                    pubkeys.size(), maxProfiles, pubkeys.size() - maxProfiles);
            pubkeys = pubkeys.subList(0, maxProfiles);
        }

        ReachCalculationRunEntity run = runRepository.save(ReachCalculationRunEntity.builder()
                .startedAt(Instant.now())
                .profilesTotal(pubkeys.size())
                .status(STATUS_RUNNING)
                .build());

        int processed = 0;
        int skipped = 0;
        int failures = 0;
        try {
            for (String pubkey : pubkeys) {
                try {
                    GatherResult result = gatherForProfile(pubkey);
                    if (!result.anyData()) {
                        skipped++;
                        failures++;
                        log.warn("reach_profile_skipped pubkey={} reason=no_relay_data", pubkey);
                        continue;
                    }
                    upsert(pubkey, result);
                    processed++;
                } catch (RuntimeException e) {
                    skipped++;
                    failures++;
                    log.warn("reach_profile_failed pubkey={} error={}", pubkey, e.toString());
                }
            }
            finishRun(run, processed, skipped, failures, STATUS_COMPLETED);
            log.info("reach_run_completed total={} processed={} skipped={} failures={}",
                    pubkeys.size(), processed, skipped, failures);
        } catch (RuntimeException e) {
            // Never leave the run row stuck in RUNNING if the pass aborts unexpectedly.
            finishRun(run, processed, skipped, failures, STATUS_FAILED);
            log.error("reach_run_failed total={} processed={} error={}", pubkeys.size(), processed, e.toString(), e);
            throw e;
        }
    }

    private void finishRun(ReachCalculationRunEntity run, int processed, int skipped, int failures, String status) {
        run.setProfilesProcessed(processed);
        run.setProfilesSkipped(skipped);
        run.setGatherFailures(failures);
        run.setFinishedAt(Instant.now());
        run.setStatus(status);
        runRepository.save(run);
    }

    private GatherResult gatherForProfile(String pubkey) {
        Set<String> relays = new LinkedHashSet<>(properties.getDefaultRelays());
        relays.addAll(nip65RelayResolver.resolve(pubkey, properties.getDefaultRelays(), properties.getRelayTimeoutSeconds()));
        return followerGatherer.gather(pubkey, relays, properties.getMaxFollowersPerRelay(), properties.getRelayTimeoutSeconds());
    }

    private void upsert(String pubkey, GatherResult result) {
        ProfileReachEntity entity = profileReachRepository.findByPubkey(pubkey)
                .orElseGet(() -> ProfileReachEntity.builder().pubkey(pubkey).build());
        entity.setReachCount(result.reachCount());
        entity.setComplete(result.complete());
        entity.setCalculatedAt(Instant.now());
        profileReachRepository.save(entity);
    }
}
