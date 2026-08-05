package xyz.tcheeric.bottin.reach;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the reach calculation on a recurring schedule (default: every 6 hours).
 *
 * <p>The interval is configurable via {@code bottin.reach.calculation-cron}. The
 * whole job can be disabled with {@code bottin.reach.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "bottin.reach", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ScheduledReachJob {

    private final ReachCalculationService reachCalculationService;

    @Scheduled(cron = "${bottin.reach.calculation-cron:0 0 */6 * * ?}")
    public void calculateReach() {
        log.info("reach_scheduled_run_started");
        try {
            reachCalculationService.calculateAll();
        } catch (RuntimeException e) {
            log.error("reach_scheduled_run_failed error={}", e.toString(), e);
        }
    }
}
