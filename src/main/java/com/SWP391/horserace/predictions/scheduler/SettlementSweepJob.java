package com.SWP391.horserace.predictions.scheduler;

import com.SWP391.horserace.predictions.repository.BettingPoolRepository;
import com.SWP391.horserace.predictions.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Automatic backstop behind the certify-hook and the manual admin resettle: periodically finds
 * OFFICIAL races that still have any non-SETTLED pool (settlement never finished — e.g. a crash
 * between certify commit and payout) and re-invokes the idempotent {@link SettlementService#settleRace}.
 * Safe to overlap with an in-flight certify-hook settlement: {@code settlePool}'s atomic pool claim
 * makes a redundant run a no-op.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementSweepJob {

    private final BettingPoolRepository bettingPoolRepository;
    private final SettlementService settlementService;

    @Scheduled(fixedDelayString = "${app.settlement.sweep-interval-ms:300000}")
    public void sweep() {
        for (UUID raceId : bettingPoolRepository.findRaceIdsWithUnsettledPoolsForOfficialRaces()) {
            try {
                settlementService.settleRace(raceId); // idempotent per race
            } catch (RuntimeException e) {
                log.warn("Settlement sweep failed for race {}", raceId, e);
            }
        }
    }
}
