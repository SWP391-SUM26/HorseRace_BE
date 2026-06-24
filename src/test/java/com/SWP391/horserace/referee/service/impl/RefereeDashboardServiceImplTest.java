package com.SWP391.horserace.referee.service.impl;

import com.SWP391.horserace.assignments.entity.PanelRole;
import com.SWP391.horserace.assignments.entity.RefereeAssignment;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.inspections.entity.InspectionStatus;
import com.SWP391.horserace.inspections.entity.RaceEntryInspection;
import com.SWP391.horserace.inspections.repository.RaceEntryInspectionRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.referee.dto.RefereeDashboardResponse;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.violations.entity.InfractionType;
import com.SWP391.horserace.violations.entity.RaceViolation;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import com.SWP391.horserace.violations.repository.RaceViolationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefereeDashboardServiceImplTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock RaceEntryInspectionRepository inspectionRepository;
    @Mock RaceViolationRepository violationRepository;
    @Mock RefereeAssignmentRepository refereeAssignmentRepository;

    private RefereeDashboardServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID raceId = UUID.randomUUID();

    private Race race;

    @BeforeEach
    void setUp() {
        service = new RefereeDashboardServiceImpl(
                raceRepository, raceEntryRepository, inspectionRepository,
                violationRepository, refereeAssignmentRepository);

        race = Race.builder()
                .raceId(raceId)
                .raceCode("R-07")
                .name("Maiden Stakes")
                .scheduledStartAt(OffsetDateTime.now().plusMinutes(30))
                .status(RaceStatus.OPEN)
                .build();
    }

    private RaceEntry entry(UUID entryId, String horseName, Integer laneNo) {
        Horse horse = Horse.builder().horseId(UUID.randomUUID()).name(horseName).build();
        TournamentRegistration reg = TournamentRegistration.builder().horse(horse).build();
        return RaceEntry.builder().entryId(entryId).race(race).registration(reg).laneNo(laneNo).build();
    }

    private RaceEntryInspection inspection(RaceEntry entry, InspectionStatus status) {
        return RaceEntryInspection.builder()
                .inspectionId(UUID.randomUUID())
                .entry(entry)
                .race(race)
                .inspectionStatus(status)
                .build();
    }

    // ── auth ──

    @Test
    void nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.getDashboard(null, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    // ── nextRace selection + countdown ──

    @Test
    void noUpcomingRace_returnsEmptyDashboard() {
        when(raceRepository.findUpcomingByStatuses(any(), any())).thenReturn(List.of());

        RefereeDashboardResponse result = service.getDashboard(userId, null);

        assertThat(result.getNextRace()).isNull();
        assertThat(result.getAlerts()).isEmpty();
        assertThat(result.getDutyRoster()).isEmpty();
        assertThat(result.getInspectionSummary().getTotal()).isZero();
    }

    @Test
    void noRaceId_picksSoonestUpcoming_andComputesCountdown() {
        when(raceRepository.findUpcomingByStatuses(any(), any())).thenReturn(List.of(race));
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of());
        when(violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING))
                .thenReturn(List.of());
        when(refereeAssignmentRepository.findByRace_RaceId(raceId)).thenReturn(List.of());

        RefereeDashboardResponse result = service.getDashboard(userId, null);

        assertThat(result.getNextRace()).isNotNull();
        assertThat(result.getNextRace().getRaceId()).isEqualTo(raceId);
        assertThat(result.getNextRace().getRaceCode()).isEqualTo("R-07");
        assertThat(result.getNextRace().getStatus()).isEqualTo(RaceStatus.OPEN);
        // ~30 minutes out, allow for test execution slack.
        assertThat(result.getNextRace().getPostTimeCountdownSeconds())
                .isBetween(1700L, 1800L);
    }

    @Test
    void givenRaceId_usesThatRace() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of());
        when(violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING))
                .thenReturn(List.of());
        when(refereeAssignmentRepository.findByRace_RaceId(raceId)).thenReturn(List.of());

        RefereeDashboardResponse result = service.getDashboard(userId, raceId);

        assertThat(result.getNextRace().getRaceId()).isEqualTo(raceId);
    }

    @Test
    void givenMissingRaceId_throwsRaceNotFound() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDashboard(userId, raceId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }

    @Test
    void nextRace_pastStart_countdownClampedToZero() {
        race.setScheduledStartAt(OffsetDateTime.now().minusMinutes(5));
        when(raceRepository.findUpcomingByStatuses(any(), any())).thenReturn(List.of(race));
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of());
        when(violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING))
                .thenReturn(List.of());
        when(refereeAssignmentRepository.findByRace_RaceId(raceId)).thenReturn(List.of());

        RefereeDashboardResponse result = service.getDashboard(userId, null);

        assertThat(result.getNextRace().getPostTimeCountdownSeconds()).isZero();
    }

    @Test
    void nextRace_noScheduledStart_countdownNull() {
        race.setScheduledStartAt(null);
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of());
        when(violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING))
                .thenReturn(List.of());
        when(refereeAssignmentRepository.findByRace_RaceId(raceId)).thenReturn(List.of());

        RefereeDashboardResponse result = service.getDashboard(userId, raceId);

        assertThat(result.getNextRace().getPostTimeCountdownSeconds()).isNull();
    }

    // ── inspectionSummary counts ──

    @Test
    void inspectionSummary_countsByStatus_missingCountAsPending() {
        RaceEntry e1 = entry(UUID.randomUUID(), "Thunderbolt", 1);
        RaceEntry e2 = entry(UUID.randomUUID(), "Night Comet", 2);
        RaceEntry e3 = entry(UUID.randomUUID(), "Silver Streak", 3);
        RaceEntry e4 = entry(UUID.randomUUID(), "No Inspection", 4); // no inspection → pending

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdWithHorse(raceId))
                .thenReturn(List.of(e1, e2, e3, e4));
        when(inspectionRepository.findByEntry_EntryIdIn(anyCollection()))
                .thenReturn(List.of(
                        inspection(e1, InspectionStatus.CLEARED),
                        inspection(e2, InspectionStatus.PENDING),
                        inspection(e3, InspectionStatus.VET_CHECK)));
        when(violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING))
                .thenReturn(List.of());
        when(refereeAssignmentRepository.findByRace_RaceId(raceId)).thenReturn(List.of());

        RefereeDashboardResponse.InspectionSummary s =
                service.getDashboard(userId, raceId).getInspectionSummary();

        assertThat(s.getTotal()).isEqualTo(4);
        assertThat(s.getCleared()).isEqualTo(1);
        assertThat(s.getVetCheck()).isEqualTo(1);
        assertThat(s.getPending()).isEqualTo(2); // one PENDING + one with no inspection
    }

    // ── alerts ──

    @Test
    void alerts_fromPendingViolationsAndVetCheckInspections() {
        RaceEntry e1 = entry(UUID.randomUUID(), "Thunderbolt", 1);
        RaceEntry e2 = entry(UUID.randomUUID(), "Night Comet", 2);

        RaceViolation v = RaceViolation.builder()
                .violationId(UUID.randomUUID())
                .race(race)
                .entry(e1)
                .infractionType(InfractionType.WHIP_USAGE)
                .severity(SeverityLevel.HIGH)
                .status(ViolationStatus.PENDING)
                .build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING))
                .thenReturn(List.of(v));
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of(e1, e2));
        when(inspectionRepository.findByEntry_EntryIdIn(anyCollection()))
                .thenReturn(List.of(inspection(e2, InspectionStatus.VET_CHECK)));
        when(refereeAssignmentRepository.findByRace_RaceId(raceId)).thenReturn(List.of());

        List<RefereeDashboardResponse.Alert> alerts =
                service.getDashboard(userId, raceId).getAlerts();

        assertThat(alerts).hasSize(2);

        RefereeDashboardResponse.Alert violationAlert = alerts.stream()
                .filter(a -> a.getType().endsWith("_PENDING")).findFirst().orElseThrow();
        assertThat(violationAlert.getType()).isEqualTo("WHIP_USAGE_PENDING");
        assertThat(violationAlert.getSeverity()).isEqualTo("HIGH");
        assertThat(violationAlert.getRefId()).isEqualTo(v.getViolationId());
        assertThat(violationAlert.getLabel()).contains("Thunderbolt");

        RefereeDashboardResponse.Alert vetAlert = alerts.stream()
                .filter(a -> a.getType().equals("VET_CHECK_REQUESTED")).findFirst().orElseThrow();
        assertThat(vetAlert.getSeverity()).isEqualTo("MEDIUM");
        assertThat(vetAlert.getEntryId()).isEqualTo(e2.getEntryId());
        assertThat(vetAlert.getLabel()).isEqualTo("Vet check requested — Night Comet");
    }

    // ── dutyRoster ──

    @Test
    void dutyRoster_mapsAssignments_stationNull() {
        User chief = User.builder().userId(UUID.randomUUID()).fullName("A. Khan").build();
        RefereeAssignment ra = RefereeAssignment.builder()
                .refAssignmentId(UUID.randomUUID())
                .race(race)
                .referee(chief)
                .panelRole(PanelRole.CHIEF)
                .build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of());
        when(violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING))
                .thenReturn(List.of());
        when(refereeAssignmentRepository.findByRace_RaceId(raceId)).thenReturn(List.of(ra));

        List<RefereeDashboardResponse.DutyRosterItem> roster =
                service.getDashboard(userId, raceId).getDutyRoster();

        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).getRefereeUserId()).isEqualTo(chief.getUserId());
        assertThat(roster.get(0).getRefereeName()).isEqualTo("A. Khan");
        assertThat(roster.get(0).getPanelRole()).isEqualTo("CHIEF");
        assertThat(roster.get(0).getStation()).isNull();
    }
}
