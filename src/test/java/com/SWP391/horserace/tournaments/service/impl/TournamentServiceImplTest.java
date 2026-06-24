package com.SWP391.horserace.tournaments.service.impl;

import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.dto.EligibilityDto;
import com.SWP391.horserace.tournaments.dto.TournamentRequest;
import com.SWP391.horserace.tournaments.dto.TournamentResponse;
import com.SWP391.horserace.tournaments.entity.CircuitTier;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.venues.entity.Venue;
import com.SWP391.horserace.venues.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentServiceImplTest {

    @Mock TournamentRepository tournamentRepository;
    @Mock UserRepository userRepository;
    @Mock VenueRepository venueRepository;
    @Mock RegistrationRepository registrationRepository;

    private TournamentServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TournamentServiceImpl(tournamentRepository, userRepository,
                venueRepository, registrationRepository);
    }

    private Tournament withStatus(UUID id, TournamentStatus status) {
        return Tournament.builder().tournamentId(id).tournamentCode("T1").name("Cup").status(status).build();
    }

    // ── §C5 status transitions ──

    @Test
    void openRegistration_fromPublished_setsRegistrationOpen() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.PUBLISHED)));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        TournamentResponse res = service.openRegistration(id);

        assertThat(res.getStatus()).isEqualTo(TournamentStatus.REGISTRATION_OPEN);
    }

    @Test
    void openRegistration_fromDraft_invalidStatus() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.DRAFT)));

        assertThatThrownBy(() -> service.openRegistration(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_INVALID_STATUS);
    }

    @Test
    void start_fromRegistrationClosed_setsOngoing() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id))
                .thenReturn(Optional.of(withStatus(id, TournamentStatus.REGISTRATION_CLOSED)));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        TournamentResponse res = service.startTournament(id);

        assertThat(res.getStatus()).isEqualTo(TournamentStatus.ONGOING);
    }

    @Test
    void start_fromOngoing_invalidStatus() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.ONGOING)));

        assertThatThrownBy(() -> service.startTournament(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_INVALID_STATUS);
    }

    @Test
    void complete_fromOngoing_setsCompleted() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.ONGOING)));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        TournamentResponse res = service.completeTournament(id);

        assertThat(res.getStatus()).isEqualTo(TournamentStatus.COMPLETED);
    }

    // ── §C2 eligibility mapping (request -> entity -> response) ──

    @Test
    void create_mapsEligibilityAndEnrichmentFields() {
        when(tournamentRepository.existsByTournamentCode("T1")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> {
            Tournament t = i.getArgument(0);
            t.setTournamentId(UUID.randomUUID());
            return t;
        });

        TournamentRequest req = TournamentRequest.builder()
                .tournamentCode("T1").name("Cup")
                .circuitTier(CircuitTier.GROUP_1)
                .totalPurse(new BigDecimal("1000000.00"))
                .entryCap(16)
                .eligibility(EligibilityDto.builder()
                        .thoroughbredsOnly(true).minAgeYears(3).requiresPreviousGroupWin(false).build())
                .build();

        TournamentResponse res = service.createTournament(req, userId);

        assertThat(res.getCircuitTier()).isEqualTo(CircuitTier.GROUP_1);
        assertThat(res.getTotalPurse()).isEqualByComparingTo("1000000.00");
        assertThat(res.getEntryCap()).isEqualTo(16);
        assertThat(res.getEligibility()).isNotNull();
        assertThat(res.getEligibility().getThoroughbredsOnly()).isTrue();
        assertThat(res.getEligibility().getMinAgeYears()).isEqualTo(3);
        assertThat(res.getEligibility().getRequiresPreviousGroupWin()).isFalse();
    }

    // ── §C3 venue link ──

    @Test
    void create_withVenueIds_linksVenues() {
        UUID venueId = UUID.randomUUID();
        Venue venue = Venue.builder().venueId(venueId).name("Meydan").city("Dubai").build();
        when(tournamentRepository.existsByTournamentCode("T1")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> {
            Tournament t = i.getArgument(0);
            t.setTournamentId(UUID.randomUUID());
            return t;
        });

        TournamentRequest req = TournamentRequest.builder()
                .tournamentCode("T1").name("Cup").venueIds(List.of(venueId)).build();

        TournamentResponse res = service.createTournament(req, userId);

        assertThat(res.getVenues()).hasSize(1);
        assertThat(res.getVenues().get(0).getName()).isEqualTo("Meydan");
        assertThat(res.getVenues().get(0).getVenueId()).isEqualTo(venueId);
    }

    @Test
    void create_unknownVenueId_throwsVenueNotFound() {
        UUID venueId = UUID.randomUUID();
        when(tournamentRepository.existsByTournamentCode("T1")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        TournamentRequest req = TournamentRequest.builder()
                .tournamentCode("T1").name("Cup").venueIds(List.of(venueId)).build();

        assertThatThrownBy(() -> service.createTournament(req, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_NOT_FOUND);
    }

    // ── §C4 registered-entries count ──

    @Test
    void getById_embedsApprovedRegistrationCount() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findByIdWithDetails(id))
                .thenReturn(Optional.of(withStatus(id, TournamentStatus.ONGOING)));
        when(registrationRepository.countByTournament_TournamentIdAndStatus(id, RegistrationStatus.APPROVED))
                .thenReturn(7L);

        TournamentResponse res = service.getTournamentById(id);

        assertThat(res.getRegisteredEntriesCount()).isEqualTo(7L);
    }
}
