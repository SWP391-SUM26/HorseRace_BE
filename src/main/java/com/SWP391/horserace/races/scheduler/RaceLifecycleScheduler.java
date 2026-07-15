package com.SWP391.horserace.races.scheduler;

import com.SWP391.horserace.races.service.RaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Periodically proposes cancellation of OPEN races that are past their registration cutoff with too
 * few confirmed runners. Semi-automatic: it does NOT cancel — it flags {@code cancelProposedAt} and
 * notifies admins, who confirm the cancellation manually. Each race is proposed in its own
 * transaction (via {@link RaceService#proposeCancel}) so one failure can't undo another's flag.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RaceLifecycleScheduler {

    private final RaceService raceService;

    @Scheduled(
            fixedDelayString = "${app.race.auto-cancel-sweep-ms:600000}",
            initialDelayString = "${app.race.auto-cancel-initial-delay-ms:60000}")
    public void sweepUnderfilledRaces() {
        for (UUID raceId : raceService.findRacesToProposeCancel()) {
            try {
                raceService.proposeCancel(raceId); // own transaction — idempotent per race
            } catch (RuntimeException e) {
                log.warn("Auto-cancel proposal failed for race {}", raceId, e);
            }
        }
    }

    /**
     * Auto-close (FR-07): transition OPEN races that are within the configured lead of their prediction
     * cutoff (fallback: scheduled start) to CLOSED, so betting opens without a manual step. Reuses the
     * auto-cancel sweep cadence. Each race is closed in its own transaction (via
     * {@link RaceService#autoClose}) so one failure can't abort the sweep. OPEN → CLOSED only and
     * idempotent, so it coexists with the auto-cancel sweep (cancel-flagged races are excluded).
     */
    @Scheduled(
            fixedDelayString = "${app.race.auto-cancel-sweep-ms:600000}",
            initialDelayString = "${app.race.auto-close-initial-delay-ms:90000}")
    public void sweepRacesToAutoClose() {
        for (UUID raceId : raceService.findRacesToAutoClose()) {
            try {
                raceService.autoClose(raceId); // own transaction — idempotent per race
            } catch (RuntimeException e) {
                log.warn("Auto-close failed for race {}", raceId, e);
            }
        }
    }
}
