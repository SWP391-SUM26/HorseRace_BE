package com.SWP391.horserace.registrations.service.impl;

import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.registrations.dto.RegistrationStatsResponse;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationStatsServiceImplTest {

    @Mock RegistrationRepository registrationRepository;
    @Mock TournamentRepository tournamentRepository;
    @Mock HorseRepository horseRepository;
    @Mock UserRepository userRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;

    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegistrationServiceImpl(
                registrationRepository, tournamentRepository, horseRepository, userRepository,
                raceRepository, raceEntryRepository);
    }

    private RegistrationRepository.StatusCount row(RegistrationStatus status, long cnt) {
        return new RegistrationRepository.StatusCount() {
            @Override public RegistrationStatus getStatus() { return status; }
            @Override public long getCnt() { return cnt; }
        };
    }

    @Test
    void getStats_noTournament_mapsCountsCorrectly() {
        when(registrationRepository.countGroupByStatus()).thenReturn(List.of(
                row(RegistrationStatus.SUBMITTED, 10),
                row(RegistrationStatus.UNDER_REVIEW, 4),
                row(RegistrationStatus.APPROVED, 20),
                row(RegistrationStatus.REJECTED, 3),
                row(RegistrationStatus.DRAFT, 2),
                row(RegistrationStatus.WITHDRAWN, 1)
        ));

        RegistrationStatsResponse stats = service.getStats(null);

        // pending = SUBMITTED + UNDER_REVIEW
        assertThat(stats.getPending()).isEqualTo(14);
        assertThat(stats.getApproved()).isEqualTo(20);
        assertThat(stats.getRejected()).isEqualTo(3);
        // total counts every status (incl DRAFT + WITHDRAWN)
        assertThat(stats.getTotal()).isEqualTo(40);

        verify(registrationRepository).countGroupByStatus();
        verifyNoMoreInteractions(registrationRepository);
    }

    @Test
    void getStats_withTournament_usesScopedQuery() {
        UUID tournamentId = UUID.randomUUID();
        when(registrationRepository.countGroupByStatusForTournament(tournamentId)).thenReturn(List.of(
                row(RegistrationStatus.SUBMITTED, 5),
                row(RegistrationStatus.APPROVED, 7)
        ));

        RegistrationStatsResponse stats = service.getStats(tournamentId);

        assertThat(stats.getPending()).isEqualTo(5);
        assertThat(stats.getApproved()).isEqualTo(7);
        assertThat(stats.getRejected()).isZero();
        assertThat(stats.getTotal()).isEqualTo(12);

        verify(registrationRepository).countGroupByStatusForTournament(tournamentId);
        verifyNoMoreInteractions(registrationRepository);
    }

    @Test
    void getStats_empty_allZero() {
        when(registrationRepository.countGroupByStatus()).thenReturn(List.of());

        RegistrationStatsResponse stats = service.getStats(null);

        assertThat(stats.getTotal()).isZero();
        assertThat(stats.getPending()).isZero();
        assertThat(stats.getApproved()).isZero();
        assertThat(stats.getRejected()).isZero();
    }
}
