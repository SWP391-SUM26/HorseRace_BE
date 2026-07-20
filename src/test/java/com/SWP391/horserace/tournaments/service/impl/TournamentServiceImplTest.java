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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentServiceImplTest {

    @Mock TournamentRepository tournamentRepository;
    @Mock UserRepository userRepository;
    @Mock VenueRepository venueRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock com.SWP391.horserace.shared.storage.ImageUploadService imageUploadService;

    private TournamentServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TournamentServiceImpl(tournamentRepository, userRepository,
                venueRepository, registrationRepository, imageUploadService);
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
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> {
            Tournament t = i.getArgument(0);
            t.setTournamentId(UUID.randomUUID());
            return t;
        });

        TournamentRequest req = TournamentRequest.builder()
                .name("Cup")
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

    // ── §C0 auto-generated tournament code (FR-04) ──

    @Test
    void createTournament_generatesUniqueCode() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> {
            Tournament t = i.getArgument(0);
            t.setTournamentId(UUID.randomUUID());
            return t;
        });

        // Request no longer carries a code — it must be system-generated.
        TournamentRequest req = TournamentRequest.builder().name("Cup").build();

        TournamentResponse res = service.createTournament(req, userId);

        assertThat(res.getTournamentCode()).isNotNull().startsWith("TRN");
    }

    @Test
    void createTournament_retriesOnCodeCollision() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        // First generated code collides, second is free -> the loop must retry.
        when(tournamentRepository.existsByTournamentCode(anyString())).thenReturn(true, false);
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> {
            Tournament t = i.getArgument(0);
            t.setTournamentId(UUID.randomUUID());
            return t;
        });

        TournamentResponse res = service.createTournament(
                TournamentRequest.builder().name("Cup").build(), userId);

        assertThat(res.getTournamentCode()).isNotNull().startsWith("TRN");
        verify(tournamentRepository, times(2)).existsByTournamentCode(anyString());
    }

    @Test
    void updateTournament_doesNotChangeCode() {
        UUID id = UUID.randomUUID();
        Tournament existing = Tournament.builder()
                .tournamentId(id).tournamentCode("TRN00007").name("Old").status(TournamentStatus.DRAFT).build();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        TournamentRequest req = TournamentRequest.builder().name("New Name").build();

        TournamentResponse res = service.updateTournament(id, req);

        assertThat(res.getTournamentCode()).isEqualTo("TRN00007"); // immutable
        assertThat(res.getName()).isEqualTo("New Name");
    }

    // ── §C3 venue link ──

    @Test
    void create_withVenueIds_linksVenues() {
        UUID venueId = UUID.randomUUID();
        Venue venue = Venue.builder().venueId(venueId).name("Meydan").city("Dubai").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> {
            Tournament t = i.getArgument(0);
            t.setTournamentId(UUID.randomUUID());
            return t;
        });

        TournamentRequest req = TournamentRequest.builder()
                .name("Cup").venueIds(List.of(venueId)).build();

        TournamentResponse res = service.createTournament(req, userId);

        assertThat(res.getVenues()).hasSize(1);
        assertThat(res.getVenues().get(0).getName()).isEqualTo("Meydan");
        assertThat(res.getVenues().get(0).getVenueId()).isEqualTo(venueId);
    }

    @Test
    void create_unknownVenueId_throwsVenueNotFound() {
        UUID venueId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        TournamentRequest req = TournamentRequest.builder()
                .name("Cup").venueIds(List.of(venueId)).build();

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

    // ── image upload (FR-06) ──

    @Test
    void uploadImage_storesNewThenBestEffortDeletesOld() {
        UUID id = UUID.randomUUID();
        Tournament t = withStatus(id, TournamentStatus.DRAFT);
        t.setImageUrl("/api/v1/files/tournaments/old.png"); // previous image to clean up
        var file = new org.springframework.mock.web.MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        String newUrl = "https://res.cloudinary.com/c/image/upload/tournaments/new.jpg";
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(t));
        when(imageUploadService.storeImageAsUrl(file, "tournaments")).thenReturn(newUrl);
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        TournamentResponse res = service.uploadImage(id, file);

        assertThat(res.getImageUrl()).isEqualTo(newUrl);
        verify(imageUploadService).deleteByUrl("/api/v1/files/tournaments/old.png");
    }

    // ── #2 date validation ──

    private void stubCreateOk() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).build()));
        lenient().when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> {
            Tournament t = i.getArgument(0);
            t.setTournamentId(UUID.randomUUID());
            return t;
        });
    }

    @Test
    void createTournament_endBeforeStart_throwsInvalidDateRange() {
        stubCreateOk();
        var now = java.time.OffsetDateTime.now();
        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .startDate(now.plusDays(5)).endDate(now.plusDays(1)).build();
        assertThatThrownBy(() -> service.createTournament(req, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    void createTournament_startInPast_throwsDateInPast() {
        stubCreateOk();
        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .startDate(java.time.OffsetDateTime.now().minusDays(2)).build();
        assertThatThrownBy(() -> service.createTournament(req, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DATE_IN_PAST);
    }

    @Test
    void createTournament_startToday_passes() {
        stubCreateOk();
        // Boundary (Risk c): today must be accepted, not rejected as past.
        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .startDate(java.time.OffsetDateTime.now()).endDate(java.time.OffsetDateTime.now().plusDays(3)).build();
        TournamentResponse res = service.createTournament(req, userId);
        assertThat(res).isNotNull();
    }

    @Test
    void createTournament_nullDates_succeeds() {
        stubCreateOk();
        // RT-HIGH: dates optional — null-safe validation must not NPE/400.
        TournamentResponse res = service.createTournament(TournamentRequest.builder().name("Cup").build(), userId);
        assertThat(res).isNotNull();
    }

    // ── FR-12: registration window within event window ──

    @Test
    void createTournament_rejectsRegistrationWindowOutsideEventWindow() {
        stubCreateOk();
        var now = java.time.OffsetDateTime.now();
        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .startDate(now.plusDays(5)).endDate(now.plusDays(10))
                .registrationOpenAt(now.plusDays(1)) // opens BEFORE the tournament starts
                .build();
        assertThatThrownBy(() -> service.createTournament(req, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REGISTRATION_WINDOW);
    }

    @Test
    void createTournament_allowsRegistrationWindowInsideEventWindow() {
        stubCreateOk();
        var now = java.time.OffsetDateTime.now();
        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .startDate(now.plusDays(5)).endDate(now.plusDays(10))
                .registrationOpenAt(now.plusDays(5)).registrationCloseAt(now.plusDays(9))
                .build();
        assertThat(service.createTournament(req, userId)).isNotNull();
    }

    @Test
    void updateTournament_appliesSameRegistrationWindowRule() {
        UUID id = UUID.randomUUID();
        Tournament existing = Tournament.builder()
                .tournamentId(id).tournamentCode("TRN00007").name("Old").status(TournamentStatus.DRAFT).build();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(existing));
        var now = java.time.OffsetDateTime.now();
        TournamentRequest req = TournamentRequest.builder().name("New")
                .startDate(now.plusDays(5)).endDate(now.plusDays(10))
                .registrationCloseAt(now.plusDays(20)) // closes AFTER the tournament ends
                .build();
        assertThatThrownBy(() -> service.updateTournament(id, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REGISTRATION_WINDOW);
    }

    // ── FR-03: forced DRAFT on create ──

    @Test
    void createTournament_forcesDraftStatus_ignoringClientStatus() {
        stubCreateOk();
        // Client tries to create directly in COMPLETED — the service must ignore it and force DRAFT.
        TournamentRequest req = TournamentRequest.builder()
                .name("Cup").status(TournamentStatus.COMPLETED).build();

        service.createTournament(req, userId);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TournamentStatus.DRAFT);
    }

    // ── FR-02: non-negative purse persists unchanged (never silently coerced) ──

    @Test
    void createTournament_persistsNonNegativePurse() {
        stubCreateOk();
        TournamentRequest req = TournamentRequest.builder()
                .name("Cup").totalPurse(new BigDecimal("500000.00")).build();

        TournamentResponse res = service.createTournament(req, userId);

        assertThat(res.getTotalPurse()).isEqualByComparingTo("500000.00");
    }
}
