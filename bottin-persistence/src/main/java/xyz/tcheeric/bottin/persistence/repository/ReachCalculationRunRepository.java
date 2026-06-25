package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.ReachCalculationRunEntity;

/**
 * Spring Data repository for {@link ReachCalculationRunEntity}.
 */
@Repository
public interface ReachCalculationRunRepository extends JpaRepository<ReachCalculationRunEntity, Long> {
}
