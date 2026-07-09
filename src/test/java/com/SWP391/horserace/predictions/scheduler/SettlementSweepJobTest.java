package com.SWP391.horserace.predictions.scheduler;

import com.SWP391.horserace.predictions.repository.BettingPoolRepository;
import com.SWP391.horserace.predictions.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementSweepJobTest {

    @Mock BettingPoolRepository bettingPoolRepository;
    @Mock SettlementService settlementService;

    @InjectMocks SettlementSweepJob job;

    @Test
    void tick_settlesEachOfficialRaceWithUnsettledPools() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(bettingPoolRepository.findRaceIdsWithUnsettledPoolsForOfficialRaces())
                .thenReturn(List.of(r1, r2));

        job.sweep();

        verify(settlementService).settleRace(r1);
        verify(settlementService).settleRace(r2);
    }

    @Test
    void tick_fullySettledRacesAreNotReprocessed() {
        when(bettingPoolRepository.findRaceIdsWithUnsettledPoolsForOfficialRaces())
                .thenReturn(List.of());

        job.sweep();

        verify(settlementService, never()).settleRace(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tick_oneFailureDoesNotStopOthers() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(bettingPoolRepository.findRaceIdsWithUnsettledPoolsForOfficialRaces())
                .thenReturn(List.of(r1, r2));
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(settlementService).settleRace(r1);

        job.sweep();

        verify(settlementService).settleRace(r1);
        verify(settlementService).settleRace(r2);
    }
}
