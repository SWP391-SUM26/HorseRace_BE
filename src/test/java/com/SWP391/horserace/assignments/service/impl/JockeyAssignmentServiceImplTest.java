package com.SWP391.horserace.assignments.service.impl;

import com.SWP391.horserace.assignments.dto.InvitationResponse;
import com.SWP391.horserace.assignments.dto.JockeyRideResponse;
import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JockeyAssignmentServiceImplTest {

    @Mock JockeyAssignmentRepository assignmentRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock JockeyProfileRepository jockeyProfileRepository;
    @Mock UserRepository userRepository;
    @Mock RaceResultRepository raceResultRepository;

    private JockeyAssignmentServiceImpl service;

    private final UUID jockeyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new JockeyAssignmentServiceImpl(
                assignmentRepository, raceEntryRepository, jockeyProfileRepository,
                userRepository, raceResultRepository);
    }

    // -- builders for a full assignment graph --

    private JockeyAssignment fullAssignment(JockeyAssignmentStatus status, BigDecimal purse) {
        User jockey = User.builder().userId(jockeyId).fullName("Irad Ortiz").build();
        User owner = User.builder().userId(UUID.randomUUID()).fullName("Owen Owner").build();
        Horse horse = Horse.builder().horseId(UUID.randomUUID()).name("Midnight Thunder").horseCode("H1").build();
        Tournament tournament = Tournament.builder().tournamentId(UUID.randomUUID()).name("T").location("Loc").build();
        Race race = Race.builder()
                .raceId(UUID.randomUUID()).name("Belmont Stakes").raceCode("R1")
                .venue("Belmont Park").totalPurse(purse).tournament(tournament)
                .scheduledStartAt(OffsetDateTime.now().plusDays(1))
                .status(RaceStatus.SCHEDULED)
                .build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(UUID.randomUUID()).owner(owner).horse(horse).build();
        RaceEntry entry = RaceEntry.builder()
                .entryId(UUID.randomUUID()).registration(reg).race(race)
                .entryCode("E1").entryNo(3).prizeEarned(new BigDecimal("450000"))
                .build();
        return JockeyAssignment.builder()
                .assignmentId(UUID.randomUUID()).entry(entry).jockey(jockey)
                .status(status).invitedAt(OffsetDateTime.now())
                .build();
    }

    // =========================================================================
    // #5 InvitationResponse purse / share mapping
    // =========================================================================

    @Test
    void mapping_setsPurseShareAndEstimatedShare() {
        JockeyAssignment a = fullAssignment(JockeyAssignmentStatus.ACCEPTED, new BigDecimal("12000000"));
        when(assignmentRepository.findByIdWithDetails(a.getAssignmentId())).thenReturn(Optional.of(a));
        // withdraw triggers mapToResponse; profile has prizePercent 10
        JockeyProfile profile = JockeyProfile.builder()
                .jockeyUserId(jockeyId).prizePercent(new BigDecimal("10")).build();
        when(jockeyProfileRepository.findById(jockeyId)).thenReturn(Optional.of(profile));
        when(assignmentRepository.save(any(JockeyAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        InvitationResponse r = service.withdrawInvitation(a.getAssignmentId(), jockeyId);

        assertThat(r.getRacePurse()).isEqualByComparingTo("12000000");
        assertThat(r.getJockeySharePercent()).isEqualByComparingTo("10");
        assertThat(r.getEstimatedShare()).isEqualByComparingTo("1200000"); // 12M * 10 / 100
    }

    @Test
    void mapping_nullPurseOrPercent_yieldsZeroEstimatedShare() {
        JockeyAssignment a = fullAssignment(JockeyAssignmentStatus.ACCEPTED, null); // no purse
        when(assignmentRepository.findByIdWithDetails(a.getAssignmentId())).thenReturn(Optional.of(a));
        when(jockeyProfileRepository.findById(jockeyId)).thenReturn(Optional.empty()); // no profile → percent 0
        when(assignmentRepository.save(any(JockeyAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        InvitationResponse r = service.withdrawInvitation(a.getAssignmentId(), jockeyId);

        assertThat(r.getRacePurse()).isNull();
        assertThat(r.getJockeySharePercent()).isEqualByComparingTo("0");
        assertThat(r.getEstimatedShare()).isEqualByComparingTo("0");
    }

    // =========================================================================
    // #9 withdrawInvitation
    // =========================================================================

    @Test
    void withdraw_nullCaller_unauthenticated() {
        assertThatThrownBy(() -> service.withdrawInvitation(UUID.randomUUID(), null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void withdraw_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(assignmentRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdrawInvitation(id, jockeyId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_NOT_FOUND);
    }

    @Test
    void withdraw_notInvitedJockey_throws() {
        JockeyAssignment a = fullAssignment(JockeyAssignmentStatus.ACCEPTED, new BigDecimal("1000"));
        when(assignmentRepository.findByIdWithDetails(a.getAssignmentId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.withdrawInvitation(a.getAssignmentId(), UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_INVITED_JOCKEY);
    }

    @Test
    void withdraw_notAccepted_throws() {
        JockeyAssignment a = fullAssignment(JockeyAssignmentStatus.INVITED, new BigDecimal("1000"));
        when(assignmentRepository.findByIdWithDetails(a.getAssignmentId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.withdrawInvitation(a.getAssignmentId(), jockeyId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_ACCEPTED);
    }

    @Test
    void withdraw_fromAccepted_setsCancelledAndRespondedAt() {
        JockeyAssignment a = fullAssignment(JockeyAssignmentStatus.ACCEPTED, new BigDecimal("1000"));
        when(assignmentRepository.findByIdWithDetails(a.getAssignmentId())).thenReturn(Optional.of(a));
        lenient().when(jockeyProfileRepository.findById(jockeyId)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(JockeyAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        InvitationResponse r = service.withdrawInvitation(a.getAssignmentId(), jockeyId);

        assertThat(a.getStatus()).isEqualTo(JockeyAssignmentStatus.CANCELLED);
        assertThat(a.getRespondedAt()).isNotNull();
        assertThat(r.getStatus()).isEqualTo(JockeyAssignmentStatus.CANCELLED);
    }

    // =========================================================================
    // #6 getMyRides PAST / UPCOMING split
    // =========================================================================

    private JockeyAssignment rideAt(RaceStatus status, OffsetDateTime start, UUID entryId) {
        Horse horse = Horse.builder().horseId(UUID.randomUUID()).name("Horse").build();
        Race race = Race.builder().raceId(UUID.randomUUID()).name("Race").venue("V")
                .status(status).scheduledStartAt(start).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(UUID.randomUUID()).horse(horse).build();
        RaceEntry entry = RaceEntry.builder()
                .entryId(entryId).registration(reg).race(race).prizeEarned(new BigDecimal("100"))
                .build();
        return JockeyAssignment.builder()
                .assignmentId(UUID.randomUUID()).entry(entry)
                .jockey(User.builder().userId(jockeyId).build())
                .status(JockeyAssignmentStatus.ACCEPTED).build();
    }

    @Test
    void getMyRides_nullCaller_unauthenticated() {
        assertThatThrownBy(() -> service.getMyRides(null, "PAST"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void getMyRides_pastAndUpcoming_splitCorrectly() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID pastEntry = UUID.randomUUID();
        UUID upcomingEntry = UUID.randomUUID();

        // FINISHED → past; SCHEDULED future → upcoming
        JockeyAssignment past = rideAt(RaceStatus.FINISHED, now.minusDays(2), pastEntry);
        JockeyAssignment upcoming = rideAt(RaceStatus.SCHEDULED, now.plusDays(2), upcomingEntry);

        when(assignmentRepository.findAcceptedRidesByJockey(jockeyId))
                .thenReturn(List.of(past, upcoming));
        // results: the past ride finished 1st
        RaceResult res = RaceResult.builder().entry(past.getEntry()).finishPosition(1).build();
        when(raceResultRepository.findByEntry_EntryIdIn(anyCollection())).thenReturn(List.of(res));

        List<JockeyRideResponse> pastRides = service.getMyRides(jockeyId, "PAST");
        assertThat(pastRides).hasSize(1);
        assertThat(pastRides.get(0).getFinishPosition()).isEqualTo(1);
        assertThat(pastRides.get(0).getVenue()).isEqualTo("V");
        assertThat(pastRides.get(0).getEarnings()).isEqualByComparingTo("100");

        List<JockeyRideResponse> upcomingRides = service.getMyRides(jockeyId, "UPCOMING");
        assertThat(upcomingRides).hasSize(1);
        assertThat(upcomingRides.get(0).getFinishPosition()).isNull(); // no result
    }

    @Test
    void getMyRides_defaultsToUpcoming_whenWhenIsNull() {
        OffsetDateTime now = OffsetDateTime.now();
        JockeyAssignment upcoming = rideAt(RaceStatus.OPEN, now.plusDays(1), UUID.randomUUID());
        JockeyAssignment past = rideAt(RaceStatus.OFFICIAL, now.minusDays(1), UUID.randomUUID());

        when(assignmentRepository.findAcceptedRidesByJockey(jockeyId))
                .thenReturn(List.of(upcoming, past));
        when(raceResultRepository.findByEntry_EntryIdIn(anyCollection())).thenReturn(List.of());

        List<JockeyRideResponse> rides = service.getMyRides(jockeyId, null);

        assertThat(rides).hasSize(1); // only the upcoming one
    }
}
