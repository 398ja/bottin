package xyz.tcheeric.bottin.reach;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.reach.ProfileReach;
import xyz.tcheeric.bottin.core.reach.ReachQueryService;
import xyz.tcheeric.bottin.persistence.repository.ProfileReachRepository;

import java.util.Optional;

/**
 * Serves stored profile reach figures from the database. Performs no live
 * calculation — figures are produced by the scheduled {@link ReachCalculationService}.
 */
@Service
@RequiredArgsConstructor
public class ReachQueryServiceImpl implements ReachQueryService {

    private final ProfileReachRepository profileReachRepository;
    private final PubkeyCodec pubkeyCodec;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileReach> findReach(String identifier) {
        String hex = pubkeyCodec.toHex(identifier);
        return profileReachRepository.findByPubkey(hex)
                .map(entity -> new ProfileReach(
                        entity.getPubkey(),
                        pubkeyCodec.toNpub(entity.getPubkey()),
                        entity.getReachCount(),
                        entity.isComplete(),
                        entity.getCalculatedAt()));
    }
}
