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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
    @Mock com.SWP391.horserace.races.repository.RaceRepository raceRepository;
    @Mock com.SWP391.horserace.wallets.service.HouseWalletService houseWalletService;
    @Mock com.SWP391.horserace.wallets.service.WalletLedgerService walletLedgerService;
    @Mock com.SWP391.horserace.prizes.repository.PrizeRepository prizeRepository;
    @Mock com.SWP391.horserace.shared.storage.ImageUploadService imageUploadService;

    private TournamentServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TournamentServiceImpl(tournamentRepository, userRepository,
                venueRepository, registrationRepository, imageUploadService, raceRepository, houseWalletService, walletLedgerService, prizeRepository);
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

    /**
     * Registration opening before the tournament starts is NORMAL — entries are taken weeks ahead.
     * The old rule demanded the opposite and every seeded tournament (all ten open 30–60 days early)
     * violated it, so an admin could not edit any tournament at all, even to fix a typo in its name.
     */
    @Test
    void createTournament_allowsRegistrationOpeningBeforeStart() {
        stubCreateOk();
        var now = java.time.OffsetDateTime.now();
        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .startDate(now.plusDays(35)).endDate(now.plusDays(40))
                .registrationOpenAt(now.plusDays(1)) // a month before the gates open — the real pattern
                .registrationCloseAt(now.plusDays(30))
                .build();
        assertThat(service.createTournament(req, userId)).isNotNull();
    }

    @Test
    void createTournament_rejectsRegistrationOpeningAfterTournamentEnds() {
        stubCreateOk();
        var now = java.time.OffsetDateTime.now();
        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .startDate(now.plusDays(5)).endDate(now.plusDays(10))
                .registrationOpenAt(now.plusDays(20)) // opens after the whole thing is over
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

    // ── partial update must not wipe stored values, nor reject untouched past dates ──

    /**
     * Seven of the ten seeded tournaments carry at least one past date. Re-validating every date on
     * every update made them permanently uneditable — a rename was rejected with DATE_IN_PAST for a
     * date the admin never touched.
     */
    @Test
    void updateTournament_pastDateLeftUnchanged_isNotRejected() {
        UUID id = UUID.randomUUID();
        var past = java.time.OffsetDateTime.now().minusMonths(6);
        Tournament existing = Tournament.builder()
                .tournamentId(id).tournamentCode("TRN00001").name("Old").status(TournamentStatus.COMPLETED)
                .startDate(past).endDate(past.plusDays(4)).build();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        // Same past dates echoed back (what the edit form sends), plus a new name.
        TournamentRequest req = TournamentRequest.builder().name("New Name")
                .startDate(past).endDate(past.plusDays(4)).build();

        assertThat(service.updateTournament(id, req).getName()).isEqualTo("New Name");
    }

    @Test
    void updateTournament_omittedDates_keepStoredValues() {
        UUID id = UUID.randomUUID();
        var start = java.time.OffsetDateTime.now().plusDays(10);
        Tournament existing = Tournament.builder()
                .tournamentId(id).tournamentCode("TRN00002").name("Old").status(TournamentStatus.DRAFT)
                .startDate(start).endDate(start.plusDays(5)).totalPurse(new BigDecimal("500")).build();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        // The FE omits blank inputs; an unconditional overwrite silently nulled the stored dates.
        TournamentResponse res = service.updateTournament(id, TournamentRequest.builder().name("New").build());

        assertThat(res.getStartDate()).isEqualTo(start);
        assertThat(res.getEndDate()).isEqualTo(start.plusDays(5));
        assertThat(res.getTotalPurse()).isEqualByComparingTo("500");
    }

    @Test
    void updateTournament_purseBelowAllocatedToRaces_isRejected() {
        UUID id = UUID.randomUUID();
        Tournament existing = Tournament.builder()
                .tournamentId(id).tournamentCode("TRN00003").name("Cup").status(TournamentStatus.DRAFT)
                .totalPurse(new BigDecimal("1000")).build();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(raceRepository.sumAllocatedPurse(id, null)).thenReturn(new BigDecimal("800"));

        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .totalPurse(new BigDecimal("700")).build(); // less than the 800 already committed

        assertThatThrownBy(() -> service.updateTournament(id, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_PURSE_BELOW_ALLOCATED);
    }

    @Test
    void updateTournament_purseExactlyEqualToAllocated_isAllowed() {
        UUID id = UUID.randomUUID();
        Tournament existing = Tournament.builder()
                .tournamentId(id).tournamentCode("TRN00004").name("Cup").status(TournamentStatus.DRAFT)
                .totalPurse(new BigDecimal("1000")).build();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(raceRepository.sumAllocatedPurse(id, null)).thenReturn(new BigDecimal("800"));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        TournamentRequest req = TournamentRequest.builder().name("Cup")
                .totalPurse(new BigDecimal("800")).build(); // exactly covers it — the boundary

        assertThat(service.updateTournament(id, req).getTotalPurse()).isEqualByComparingTo("800");
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

    // ── sponsor funding + end-of-tournament reconcile ──

    /**
     * Publishing commits the purse: the organiser's money is credited to the house so prizes have a
     * source. Before this, PRIZE was the one category minted from nothing.
     */
    @Test
    void publishTournament_creditsSponsorPurseToHouse() {
        UUID id = UUID.randomUUID();
        UUID houseId = UUID.randomUUID();
        Tournament t = withStatus(id, TournamentStatus.DRAFT);
        t.setTotalPurse(new BigDecimal("1000000"));
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(t));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));
        when(houseWalletService.houseUserId()).thenReturn(houseId);

        service.publishTournament(id);

        verify(walletLedgerService).applyEntry(
                eq(houseId),
                eq(com.SWP391.horserace.wallets.entity.EntryType.CREDIT),
                eq(com.SWP391.horserace.wallets.entity.TxnCategory.SPONSOR),
                argThat(a -> a.compareTo(new BigDecimal("1000000")) == 0),
                eq("TOURNAMENT_PUBLISH"), eq(id));
    }

    /**
     * Idempotency is structural: publish is the only way out of DRAFT, so a second call throws
     * before any money moves. No ledger probe needed — and none must be relied on.
     */
    @Test
    void publishTournament_secondCall_movesNoMoney() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.publishTournament(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_INVALID_STATUS);

        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void publishTournament_noPurse_movesNoMoney() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.DRAFT)));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        service.publishTournament(id);

        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void completeTournament_racesStillUnfinished_isRejected() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.ONGOING)));
        when(raceRepository.countNonTerminalByTournament(id)).thenReturn(2L);

        assertThatThrownBy(() -> service.completeTournament(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_HAS_NON_TERMINAL_RACES);

        verify(prizeRepository, never()).markPaidForTournament(any(), any());
    }

    /**
     * The gate must run even when the tournament is ALREADY COMPLETED. Checking it only on the
     * ONGOING path would let a re-run sweep prizes for races that were never certified.
     */
    @Test
    void completeTournament_alreadyCompletedButRacesUnfinished_stillRejected() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.COMPLETED)));
        when(raceRepository.countNonTerminalByTournament(id)).thenReturn(1L);

        assertThatThrownBy(() -> service.completeTournament(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_HAS_NON_TERMINAL_RACES);

        verify(prizeRepository, never()).markPaidForTournament(any(), any());
    }

    @Test
    void completeTournament_closesTheBooks_marksPrizesPaid() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.ONGOING)));
        when(raceRepository.countNonTerminalByTournament(id)).thenReturn(0L);
        when(prizeRepository.markPaidForTournament(eq(id), any())).thenReturn(12);
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        TournamentResponse res = service.completeTournament(id);

        assertThat(res.getStatus()).isEqualTo(TournamentStatus.COMPLETED);
        verify(prizeRepository).markPaidForTournament(eq(id), any());
        // Closing the books is a STATUS FLIP. The money already moved at certify — paying again
        // here would pay every winner twice.
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void completeTournament_calledTwice_isIdempotent() {
        UUID id = UUID.randomUUID();
        when(tournamentRepository.findById(id)).thenReturn(Optional.of(withStatus(id, TournamentStatus.COMPLETED)));
        when(raceRepository.countNonTerminalByTournament(id)).thenReturn(0L);
        // Nothing left in AWARDED — the predicate in the bulk update is what makes the re-run a no-op.
        when(prizeRepository.markPaidForTournament(eq(id), any())).thenReturn(0);

        TournamentResponse res = service.completeTournament(id);

        assertThat(res.getStatus()).isEqualTo(TournamentStatus.COMPLETED);
        verify(tournamentRepository, never()).save(any(Tournament.class));
    }
}
