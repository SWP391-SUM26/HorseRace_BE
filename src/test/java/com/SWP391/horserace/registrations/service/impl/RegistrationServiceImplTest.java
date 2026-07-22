package com.SWP391.horserace.registrations.service.impl;

import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.registrations.dto.RegistrationRequest;
import com.SWP391.horserace.registrations.dto.RegistrationResponse;
import com.SWP391.horserace.registrations.dto.RejectRegistrationRequest;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock RegistrationRepository registrationRepository;
    @Mock TournamentRepository tournamentRepository;
    @Mock HorseRepository horseRepository;
    @Mock UserRepository userRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock com.SWP391.horserace.races.service.RaceEntryGate raceEntryGate;
    @Mock com.SWP391.horserace.attachments.repository.AttachmentRepository attachmentRepository;

    private RegistrationServiceImpl service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID tournamentId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();

    private User owner;
    private Horse horse;
    private Tournament tournament;

    @BeforeEach
    void setUp() {
        service = new RegistrationServiceImpl(
                registrationRepository, tournamentRepository, horseRepository, userRepository,
                raceRepository, raceEntryRepository, raceEntryGate, attachmentRepository);
        // #7: by default a registration HAS its owner-uploaded document (approve tests focus on other logic).
        org.mockito.Mockito.lenient().when(attachmentRepository
                .existsByOwnerEntityTypeAndOwnerEntityIdAndUploadedBy_UserId(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        owner = User.builder().userId(ownerId).fullName("Owen Owner").build();
        horse = Horse.builder().horseId(horseId).owner(owner).horseCode("HRS0001").name("Thunder").build();
        tournament = Tournament.builder()
                .tournamentId(tournamentId)
                .name("Spring Cup")
                .status(TournamentStatus.PUBLISHED)
                .build();
    }

    private RegistrationRequest req() {
        return new RegistrationRequest(tournamentId, horseId, null);
    }

    private RegistrationRequest reqWithRace(UUID raceId) {
        return new RegistrationRequest(tournamentId, horseId, raceId);
    }

    // ── submit ──

    @Test
    void submit_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.submitRegistration(null, req()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void submit_horseNotFound_throws() {
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitRegistration(ownerId, req()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HORSE_NOT_FOUND);
    }

    @Test
    void submit_byNonOwnerNonAdmin_rejected() {
        UUID strangerId = UUID.randomUUID();
        User stranger = User.builder().userId(strangerId)
                .role(Role.builder().roleCode("HORSE_OWNER").build()).build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(userRepository.findByUserIdAndDeletedFalse(strangerId)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.submitRegistration(strangerId, req()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_HORSE_OWNER);
    }

    @Test
    void submit_tournamentNotAcceptingRegistration_rejected() {
        tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED);
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> service.submitRegistration(ownerId, req()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_NOT_ACCEPTING_REGISTRATION);
    }

    @Test
    void submit_duplicate_rejected() {
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        // A LIVE registration blocks. (A terminated one must not — see submit_afterRejected_isAllowed.)
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.of(TournamentRegistration.builder()
                        .registrationId(UUID.randomUUID()).status(RegistrationStatus.SUBMITTED).build()));

        assertThatThrownBy(() -> service.submitRegistration(ownerId, req()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_ALREADY_EXISTS);
    }

    @Test
    void submit_happyPath_setsSubmittedStatusCodeAndSubmittedAt() {
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.empty());
        when(userRepository.findByUserIdAndDeletedFalse(ownerId)).thenReturn(Optional.of(owner));
        when(registrationRepository.count()).thenReturn(4L);
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.submitRegistration(ownerId, req());

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.SUBMITTED);
        assertThat(res.getRegistrationCode()).isEqualTo("REG00005");
        assertThat(res.getSubmittedAt()).isNotNull();
        assertThat(res.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(res.getTournamentId()).isEqualTo(tournamentId);
        assertThat(res.getHorseId()).isEqualTo(horseId);
    }

    @Test
    void submit_byAdminForOthersHorse_allowed() {
        UUID adminId = UUID.randomUUID();
        User admin = User.builder().userId(adminId)
                .role(Role.builder().roleCode("ADMIN").build()).build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.empty());
        when(userRepository.findByUserIdAndDeletedFalse(adminId)).thenReturn(Optional.of(admin));
        when(registrationRepository.count()).thenReturn(0L);
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.submitRegistration(adminId, req());

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.SUBMITTED);
        assertThat(res.getRegistrationCode()).isEqualTo("REG00001");
    }

    @Test
    void submit_withRaceInTournament_setsRaceOnRegistration() {
        UUID raceId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).name("Race 1")
                .tournament(tournament).status(RaceStatus.SCHEDULED).build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.empty());
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(ownerId)).thenReturn(Optional.of(owner));
        when(registrationRepository.count()).thenReturn(0L);
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.submitRegistration(ownerId, reqWithRace(raceId));

        assertThat(res.getRaceId()).isEqualTo(raceId);
        assertThat(res.getRaceName()).isEqualTo("Race 1");
    }

    @Test
    void submit_withRaceNotFound_throws() {
        UUID raceId = UUID.randomUUID();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.empty());
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitRegistration(ownerId, reqWithRace(raceId)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_NOT_FOUND);
    }

    @Test
    void submit_withRaceOfDifferentTournament_mismatch() {
        UUID raceId = UUID.randomUUID();
        Tournament otherTournament = Tournament.builder().tournamentId(UUID.randomUUID()).name("Other").build();
        Race race = Race.builder().raceId(raceId).name("Foreign Race")
                .tournament(otherTournament).status(RaceStatus.SCHEDULED).build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.empty());
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.submitRegistration(ownerId, reqWithRace(raceId)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_TOURNAMENT_MISMATCH);
    }

    // ── approve ──

    @Test
    void approve_fromSubmitted_approves() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        User reviewer = User.builder().userId(reviewerId).fullName("Adam Admin").build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(userRepository.findByUserIdAndDeletedFalse(reviewerId)).thenReturn(Optional.of(reviewer));
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.approveRegistration(reviewerId, id);

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(res.getApprovedByUserId()).isEqualTo(reviewerId);
        assertThat(res.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_noDocument_throwsRegistrationDocumentRequired() {
        // #7: no TOURNAMENT_REGISTRATION attachment → approval blocked.
        UUID id = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(attachmentRepository.existsByOwnerEntityTypeAndOwnerEntityIdAndUploadedBy_UserId(
                "TOURNAMENT_REGISTRATION", id, ownerId)).thenReturn(false);

        assertThatThrownBy(() -> service.approveRegistration(UUID.randomUUID(), id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_DOCUMENT_REQUIRED);
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void approve_fromApproved_invalidStatus() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).status(RegistrationStatus.APPROVED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> service.approveRegistration(reviewerId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_INVALID_STATUS);
    }

    @Test
    void approve_withChosenRace_createsRaceEntry() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID raceId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).name("Race 1")
                .tournament(tournament).status(RaceStatus.OPEN).maxParticipants(8).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse).race(race)
                .status(RegistrationStatus.SUBMITTED).build();
        User reviewer = User.builder().userId(reviewerId).fullName("Adam Admin").build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(userRepository.findByUserIdAndDeletedFalse(reviewerId)).thenReturn(Optional.of(reviewer));
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(raceEntryRepository.countByRace_RaceId(raceId)).thenReturn(0L);
        when(raceEntryRepository.count()).thenReturn(0L);
        when(raceEntryRepository.existsByEntryCode(any())).thenReturn(false);
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.approveRegistration(reviewerId, id);

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.APPROVED);

        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository).save(captor.capture());
        RaceEntry savedEntry = captor.getValue();
        assertThat(savedEntry.getRace()).isEqualTo(race);
        assertThat(savedEntry.getRegistration()).isEqualTo(reg);
        assertThat(savedEntry.getStatus()).isEqualTo(RaceEntryStatus.ENTERED);
        assertThat(savedEntry.getEntryCode()).isEqualTo("ENT00001");
    }

    @Test
    void approve_withoutRace_noRaceEntryCreated() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.approveRegistration(reviewerId, id);

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
        verify(raceEntryRepository, never()).save(any());
    }

    @Test
    void approve_intoRaceNotOpen_throws() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID raceId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).name("Race 1")
                .tournament(tournament).status(RaceStatus.CLOSED).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse).race(race)
                .status(RegistrationStatus.SUBMITTED).build();
        User reviewer = User.builder().userId(reviewerId).fullName("Adam Admin").build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(userRepository.findByUserIdAndDeletedFalse(reviewerId)).thenReturn(Optional.of(reviewer));
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.approveRegistration(reviewerId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_NOT_OPEN_FOR_ENTRY);
        verify(raceEntryRepository, never()).save(any());
    }

    // ── reject ──

    @Test
    void reject_fromSubmitted_rejectsWithReason() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.rejectRegistration(
                reviewerId, id, new RejectRegistrationRequest("Horse failed vet check"));

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(res.getRejectionReason()).isEqualTo("Horse failed vet check");
        assertThat(res.getReviewedAt()).isNotNull();
        // reject must NOT populate approvedBy (that field is for the approver only)
        assertThat(res.getApprovedByUserId()).isNull();
    }

    @Test
    void reject_fromWithdrawn_invalidStatus() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).status(RegistrationStatus.WITHDRAWN).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> service.rejectRegistration(
                reviewerId, id, new RejectRegistrationRequest("too late")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_INVALID_STATUS);
    }

    // ── withdraw ──

    @Test
    void withdraw_byOwnerFromSubmitted_withdraws() {
        UUID id = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.withdrawRegistration(ownerId, id);

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.WITHDRAWN);
    }

    @Test
    void withdraw_byNonOwnerNonAdmin_rejected() {
        UUID id = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        User stranger = User.builder().userId(strangerId)
                .role(Role.builder().roleCode("HORSE_OWNER").build()).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(userRepository.findByUserIdAndDeletedFalse(strangerId)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.withdrawRegistration(strangerId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_REGISTRATION_OWNER);
    }

    @Test
    void withdraw_fromApproved_invalidStatus() {
        UUID id = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).status(RegistrationStatus.APPROVED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> service.withdrawRegistration(ownerId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_INVALID_STATUS);
    }

    @Test
    void getById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRegistrationById(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_NOT_FOUND);
    }

    @Test
    void getById_mapsCategory() {
        UUID id = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id)
                .registrationCode("REG-001")
                .owner(owner)
                .horse(horse)
                .tournament(tournament)
                .status(RegistrationStatus.APPROVED)
                .category("GROUP_1")
                .build();
        when(registrationRepository.findByIdWithDetails(id)).thenReturn(Optional.of(reg));

        RegistrationResponse res = service.getRegistrationById(id);

        assertThat(res.getCategory()).isEqualTo("GROUP_1");
    }

    // ── delete (soft-remove + scratch race entry) — Feature #10 ──

    @Test
    void deleteRegistration_nonApproved_removesWithoutEntryLookup() {
        UUID id = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));

        service.deleteRegistration(currentUserId, id);

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.REMOVED);
        verify(raceEntryRepository, never()).findByRegistration_RegistrationId(any());
    }

    @Test
    void deleteRegistration_approved_scratchesRaceEntry() {
        UUID id = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Race race = Race.builder().raceId(UUID.randomUUID()).name("Race 1")
                .tournament(tournament).status(RaceStatus.OPEN).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.APPROVED).build();
        RaceEntry entry = RaceEntry.builder()
                .entryId(UUID.randomUUID()).race(race).registration(reg)
                .status(RaceEntryStatus.ENTERED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(raceEntryRepository.findByRegistration_RegistrationId(id)).thenReturn(Optional.of(entry));

        service.deleteRegistration(currentUserId, id);

        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RaceEntryStatus.SCRATCHED);
        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.REMOVED);
    }

    @Test
    void deleteRegistration_approved_raceFinalized_refuses() {
        // Race FINISHED (an OFFICIAL race behaves identically) → refuse: reg stays APPROVED,
        // and the entry is never scratched.
        UUID id = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Race race = Race.builder().raceId(UUID.randomUUID()).name("Race 1")
                .tournament(tournament).status(RaceStatus.FINISHED).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.APPROVED).build();
        RaceEntry entry = RaceEntry.builder()
                .entryId(UUID.randomUUID()).race(race).registration(reg)
                .status(RaceEntryStatus.ENTERED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(raceEntryRepository.findByRegistration_RegistrationId(id)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.deleteRegistration(currentUserId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_ALREADY_FINALIZED);

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
        verify(raceEntryRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void deleteRegistration_null_user_unauthenticated() {
        assertThatThrownBy(() -> service.deleteRegistration(null, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    // ── entry fee: charged at submit, returned on every exit ──

    private void stubCleanSubmit() {
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.empty());
        when(userRepository.findByUserIdAndDeletedFalse(ownerId)).thenReturn(Optional.of(owner));
        when(registrationRepository.count()).thenReturn(0L);
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * The whole point of the change: the fee lands on the owner at the moment THEY act. It used to
     * be taken inside the referee's approve transaction, so a thin wallet failed the referee with an
     * error about someone else's money and silently discarded their approval.
     */
    @Test
    void submit_withRace_chargesTheEntryFee() {
        UUID raceId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).tournament(tournament).build();
        stubCleanSubmit();
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));

        service.submitRegistration(ownerId, reqWithRace(raceId));

        verify(raceEntryGate).chargeEntryFeeOnce(any(TournamentRegistration.class), eq(race));
    }

    /** No race chosen means no fee is knowable — it lives on the race. */
    @Test
    void submit_withoutRace_chargesNothing() {
        stubCleanSubmit();

        service.submitRegistration(ownerId, req());

        verify(raceEntryGate, never()).chargeEntryFeeOnce(any(), any());
    }

    @Test
    void rejectRegistration_returnsTheFee() {
        UUID id = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> i.getArgument(0));

        service.rejectRegistration(ownerId, id, new RejectRegistrationRequest("Hồ sơ thiếu"));

        verify(raceEntryGate).refundEntryFeeOnce(reg);
    }

    @Test
    void withdrawRegistration_returnsTheFee() {
        UUID id = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(id).owner(owner).tournament(tournament).horse(horse)
                .status(RegistrationStatus.SUBMITTED).build();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> i.getArgument(0));

        service.withdrawRegistration(ownerId, id);

        verify(raceEntryGate).refundEntryFeeOnce(reg);
    }

    /**
     * A rejected registration must not lock the horse out of the tournament forever. The owner paid,
     * was refunded, and would otherwise have no way back in — the DB UNIQUE(tournament_id, horse_id)
     * permits exactly one row, so the dead one is reused.
     */
    @Test
    void submit_afterRejected_reusesTheRowAndChargesAgain() {
        UUID oldId = UUID.randomUUID();
        TournamentRegistration rejected = TournamentRegistration.builder()
                .registrationId(oldId).owner(owner).tournament(tournament).horse(horse)
                .registrationCode("REG00009")
                .status(RegistrationStatus.REJECTED)
                .rejectionReason("Hồ sơ thiếu")
                .entryFeeAmount(new java.math.BigDecimal("1500000"))
                .entryFeePaidAt(OffsetDateTime.now().minusDays(1))
                .entryFeeRefundedAt(OffsetDateTime.now().minusHours(1))
                .build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.of(rejected));
        when(userRepository.findByUserIdAndDeletedFalse(ownerId)).thenReturn(Optional.of(owner));
        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> i.getArgument(0));

        RegistrationResponse res = service.submitRegistration(ownerId, req());

        assertThat(res.getStatus()).isEqualTo(RegistrationStatus.SUBMITTED);
        assertThat(res.getRegistrationCode()).isEqualTo("REG00009");   // same row, code kept
        // Both stamps cleared, so the new attempt is charged rather than seen as already paid.
        assertThat(rejected.getEntryFeePaidAt()).isNull();
        assertThat(rejected.getEntryFeeRefundedAt()).isNull();
        assertThat(rejected.getRejectionReason()).isNull();
    }

    @Test
    void submit_whileAnotherIsStillLive_isRejected() {
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findFirstByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(Optional.of(TournamentRegistration.builder()
                        .registrationId(UUID.randomUUID()).status(RegistrationStatus.APPROVED).build()));

        assertThatThrownBy(() -> service.submitRegistration(ownerId, req()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_ALREADY_EXISTS);
    }
}
