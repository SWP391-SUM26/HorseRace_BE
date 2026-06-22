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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                raceRepository, raceEntryRepository);

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
        when(registrationRepository.existsByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submitRegistration(ownerId, req()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_ALREADY_EXISTS);
    }

    @Test
    void submit_happyPath_setsSubmittedStatusCodeAndSubmittedAt() {
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.existsByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(false);
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
        when(registrationRepository.existsByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(false);
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
        when(registrationRepository.existsByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(false);
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
        when(registrationRepository.existsByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(false);
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
        when(registrationRepository.existsByTournament_TournamentIdAndHorse_HorseId(tournamentId, horseId))
                .thenReturn(false);
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
}
