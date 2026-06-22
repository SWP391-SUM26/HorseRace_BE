package com.SWP391.horserace.referee.service.impl;

import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.referee.dto.CreateReportRequest;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;
import com.SWP391.horserace.referee.dto.UpdateReportRequest;
import com.SWP391.horserace.reports.entity.RefereeReport;
import com.SWP391.horserace.reports.entity.ReportStatus;
import com.SWP391.horserace.reports.entity.ReportType;
import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.reports.repository.RefereeReportRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefereeServiceImplTest {

    @Mock RefereeReportRepository refereeReportRepository;
    @Mock HorseRepository horseRepository;
    @Mock RaceRepository raceRepository;
    @Mock UserRepository userRepository;

    private RefereeServiceImpl service;

    private final UUID currentUserId = UUID.randomUUID();
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RefereeServiceImpl(
                refereeReportRepository, horseRepository, raceRepository, userRepository);
    }

    private Race race() {
        return Race.builder().raceId(raceId).build();
    }

    private User author() {
        return User.builder().userId(currentUserId).fullName("Ref Eric").build();
    }

    // ── createReport ──

    @Test
    void createReport_raceNotFound_throws() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.empty());

        CreateReportRequest req = new CreateReportRequest(raceId, null, "sum", "dec", SeverityLevel.HIGH);

        assertThatThrownBy(() -> service.createReport(currentUserId, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_NOT_FOUND);
    }

    @Test
    void createReport_happyPath_setsDraftStatusDefaultTypeAndAuthor() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race()));
        when(userRepository.findByUserIdAndDeletedFalse(currentUserId)).thenReturn(Optional.of(author()));
        when(refereeReportRepository.save(any(RefereeReport.class))).thenAnswer(i -> i.getArgument(0));

        // reportType omitted (null) → defaults to VIOLATION
        CreateReportRequest req = new CreateReportRequest(raceId, null, "Foul play", "Disqualified", SeverityLevel.CRITICAL);

        var res = service.createReport(currentUserId, req);

        assertThat(res.getReportStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(res.getReportType()).isEqualTo(ReportType.VIOLATION);
        assertThat(res.getRaceId()).isEqualTo(raceId);
        assertThat(res.getAuthorUserId()).isEqualTo(currentUserId);
        assertThat(res.getAuthorName()).isEqualTo("Ref Eric");
        assertThat(res.getSummary()).isEqualTo("Foul play");
        assertThat(res.getSeverityLevel()).isEqualTo(SeverityLevel.CRITICAL);
    }

    @Test
    void createReport_nullPrincipal_unauthenticated() {
        CreateReportRequest req = new CreateReportRequest(raceId, ReportType.INCIDENT, null, null, null);

        assertThatThrownBy(() -> service.createReport(null, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    // ── updateReport ──

    @Test
    void updateReport_fromDraft_appliesFields() {
        UUID id = UUID.randomUUID();
        RefereeReport report = RefereeReport.builder()
                .reportId(id).race(race()).author(author())
                .reportType(ReportType.VIOLATION).reportStatus(ReportStatus.DRAFT)
                .build();
        when(refereeReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(refereeReportRepository.save(any(RefereeReport.class))).thenAnswer(i -> i.getArgument(0));

        UpdateReportRequest req = new UpdateReportRequest(
                ReportType.OBJECTION, "Updated summary", "Updated decision", SeverityLevel.LOW);

        var res = service.updateReport(currentUserId, id, req);

        assertThat(res.getReportType()).isEqualTo(ReportType.OBJECTION);
        assertThat(res.getSummary()).isEqualTo("Updated summary");
        assertThat(res.getDecision()).isEqualTo("Updated decision");
        assertThat(res.getSeverityLevel()).isEqualTo(SeverityLevel.LOW);
        assertThat(res.getReportStatus()).isEqualTo(ReportStatus.DRAFT);
    }

    @Test
    void updateReport_fromSubmitted_invalidStatus() {
        UUID id = UUID.randomUUID();
        RefereeReport report = RefereeReport.builder()
                .reportId(id).race(race()).author(author())
                .reportStatus(ReportStatus.SUBMITTED)
                .build();
        when(refereeReportRepository.findById(id)).thenReturn(Optional.of(report));

        UpdateReportRequest req = new UpdateReportRequest(null, "x", null, null);

        assertThatThrownBy(() -> service.updateReport(currentUserId, id, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_INVALID_STATUS);
    }

    // ── submitReport ──

    @Test
    void submitReport_fromDraft_setsSubmittedAndTimestamp() {
        UUID id = UUID.randomUUID();
        RefereeReport report = RefereeReport.builder()
                .reportId(id).race(race()).author(author())
                .reportStatus(ReportStatus.DRAFT)
                .build();
        when(refereeReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(refereeReportRepository.save(any(RefereeReport.class))).thenAnswer(i -> i.getArgument(0));

        var res = service.submitReport(currentUserId, id);

        assertThat(res.getReportStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(res.getSubmittedAt()).isNotNull();
        assertThat(report.getReportStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(report.getSubmittedAt()).isNotNull();
    }

    @Test
    void submitReport_fromSubmitted_invalidStatus() {
        UUID id = UUID.randomUUID();
        RefereeReport report = RefereeReport.builder()
                .reportId(id).race(race()).author(author())
                .reportStatus(ReportStatus.SUBMITTED)
                .build();
        when(refereeReportRepository.findById(id)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.submitReport(currentUserId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_INVALID_STATUS);
    }

    // ── recordHealthCheck ──

    @Test
    void healthCheck_horseNotFound_throws() {
        UUID horseId = UUID.randomUUID();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.empty());

        HealthCheckRequest req = new HealthCheckRequest(HorseHealthStatus.HEALTHY, "ok");

        assertThatThrownBy(() -> service.recordHealthCheck(currentUserId, horseId, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HORSE_NOT_FOUND);
    }

    @Test
    void healthCheck_happyPath_setsStatusAndLastCheck() {
        UUID horseId = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(horseId).name("Thunder")
                .healthStatus(HorseHealthStatus.HEALTHY).build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        HealthCheckRequest req = new HealthCheckRequest(HorseHealthStatus.INJURED, "Limping");

        MedicalStatusResponse res = service.recordHealthCheck(currentUserId, horseId, req);

        assertThat(res.getHealthStatus()).isEqualTo(HorseHealthStatus.INJURED);
        assertThat(res.getLastHealthCheckAt()).isNotNull();
        assertThat(res.getMedicalNote()).isEqualTo("Limping");
        assertThat(res.getHorseName()).isEqualTo("Thunder");
        assertThat(horse.getHealthStatus()).isEqualTo(HorseHealthStatus.INJURED);
        assertThat(horse.getLastHealthCheckAt()).isNotNull();
    }

    @Test
    void healthCheck_nullPrincipal_unauthenticated() {
        UUID horseId = UUID.randomUUID();
        HealthCheckRequest req = new HealthCheckRequest(HorseHealthStatus.HEALTHY, null);

        assertThatThrownBy(() -> service.recordHealthCheck(null, horseId, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }
}
