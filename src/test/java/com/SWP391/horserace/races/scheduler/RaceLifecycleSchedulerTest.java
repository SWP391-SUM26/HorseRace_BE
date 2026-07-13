package com.SWP391.horserace.races.scheduler;

import com.SWP391.horserace.races.service.RaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceLifecycleSchedulerTest {

    @Mock RaceService raceService;

    @InjectMocks RaceLifecycleScheduler scheduler;

    // ── auto-cancel sweep ──

    @Test
    void sweepUnderfilledRaces_proposesEachCandidate() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(raceService.findRacesToProposeCancel()).thenReturn(List.of(r1, r2));

        scheduler.sweepUnderfilledRaces();

        verify(raceService).proposeCancel(r1);
        verify(raceService).proposeCancel(r2);
    }

    @Test
    void sweepUnderfilledRaces_oneFailureDoesNotStopOthers() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(raceService.findRacesToProposeCancel()).thenReturn(List.of(r1, r2));
        doThrow(new RuntimeException("boom")).when(raceService).proposeCancel(r1);

        scheduler.sweepUnderfilledRaces();

        verify(raceService).proposeCancel(r1);
        verify(raceService).proposeCancel(r2); // still processed after the failure
    }

    // ── auto-close sweep (FR-07) ──

    @Test
    void sweepRacesToAutoClose_closesEachCandidate() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(raceService.findRacesToAutoClose()).thenReturn(List.of(r1, r2));

        scheduler.sweepRacesToAutoClose();

        verify(raceService).autoClose(r1);
        verify(raceService).autoClose(r2);
    }

    @Test
    void sweepRacesToAutoClose_noCandidates_noClose() {
        when(raceService.findRacesToAutoClose()).thenReturn(List.of());

        scheduler.sweepRacesToAutoClose();

        verify(raceService, never()).autoClose(any());
    }

    @Test
    void sweepRacesToAutoClose_oneFailureDoesNotStopOthers() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(raceService.findRacesToAutoClose()).thenReturn(List.of(r1, r2));
        doThrow(new RuntimeException("boom")).when(raceService).autoClose(r1);

        scheduler.sweepRacesToAutoClose();

        verify(raceService).autoClose(r1);
        verify(raceService).autoClose(r2); // per-id failure swallowed — sweep continues
    }
}
