package com.SWP391.horserace.violations.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.penalties.entity.Penalty;
import com.SWP391.horserace.penalties.entity.PenaltyStatus;
import com.SWP391.horserace.penalties.entity.PenaltyType;
import com.SWP391.horserace.penalties.repository.PenaltyRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.violations.dto.CreateViolationRequest;
import com.SWP391.horserace.violations.dto.RulingRequest;
import com.SWP391.horserace.violations.dto.RulingResponse;
import com.SWP391.horserace.violations.dto.ViolationDetailResponse;
import com.SWP391.horserace.violations.dto.ViolationListItemResponse;
import com.SWP391.horserace.violations.entity.InfractionType;
import com.SWP391.horserace.violations.entity.RaceViolation;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import com.SWP391.horserace.violations.repository.RaceViolationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationServiceImplTest {

    @Mock RaceViolationRepository violationRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;
    @Mock PenaltyRepository penaltyRepository;
    @Mock UserRepository userRepository;

    private ViolationServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID violationId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();

    private Race race;
    private RaceEntry entry;
    private User reporter;
    private User jockey;

    @BeforeEach
    void setUp() {
        service = new ViolationServiceImpl(
                violationRepository, raceRepository, raceEntryRepository,
                jockeyAssignmentRepository, penaltyRepository, userRepository);

        race = Race.builder().raceId(raceId).raceCode("R-07").build();

        Horse horse = Horse.builder().horseId(horseId).name("Thunderbolt").build();
        TournamentRegistration reg = TournamentRegistration.builder().horse(horse).build();
        entry = RaceEntry.builder().entryId(entryId).race(race).registration(reg).build();

        reporter = User.builder().userId(userId).fullName("A. Khan").build();
        jockey = User.builder().userId(UUID.randomUUID()).fullName("M. Reyes").build();
    }

    private CreateViolationRequest createReq(UUID entry) {
        return new CreateViolationRequest(entry, InfractionType.WHIP_USAGE, SeverityLevel.MEDIUM,
                3, 84000L, "Excessive whip use.", "Rule 142(b)", null);
    }

    // ── create ──

    @Test
    void create_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.createViolation(null, raceId, createReq(entryId)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void create_pending_resolvesJockey_andStampsReporter() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(reporter));
        when(jockeyAssignmentRepository.findAcceptedByEntryId(entryId))
                .thenReturn(Optional.of(JockeyAssignment.builder().entry(entry).jockey(jockey).build()));
        when(violationRepository.save(any(RaceViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        ViolationDetailResponse resp = service.createViolation(userId, raceId, createReq(entryId));

        ArgumentCaptor<RaceViolation> captor = ArgumentCaptor.forClass(RaceViolation.class);
        verify(violationRepository).save(captor.capture());
        RaceViolation saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(ViolationStatus.PENDING);
        assertThat(saved.getReportedBy()).isEqualTo(reporter);
        assertThat(saved.getJockey()).isEqualTo(jockey);
        assertThat(saved.getInfractionType()).isEqualTo(InfractionType.WHIP_USAGE);
        assertThat(saved.getSeverity()).isEqualTo(SeverityLevel.MEDIUM);
        assertThat(saved.getRace()).isEqualTo(race);
        assertThat(saved.getEntry()).isEqualTo(entry);

        assertThat(resp.getHorseName()).isEqualTo("Thunderbolt");
        assertThat(resp.getJockeyName()).isEqualTo("M. Reyes");
        assertThat(resp.getStatus()).isEqualTo(ViolationStatus.PENDING);
        assertThat(resp.getRuling()).isNull();
    }

    @Test
    void create_nullEntry_allowed_noJockeyLookup() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(reporter));
        when(violationRepository.save(any(RaceViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        ViolationDetailResponse resp = service.createViolation(userId, raceId, createReq(null));

        verify(jockeyAssignmentRepository, never()).findAcceptedByEntryId(any());
        assertThat(resp.getEntryId()).isNull();
        assertThat(resp.getHorseName()).isNull();
        assertThat(resp.getJockeyName()).isNull();
        assertThat(resp.getStatus()).isEqualTo(ViolationStatus.PENDING);
    }

    @Test
    void create_entryNotInRace_mismatch() {
        Race otherRace = Race.builder().raceId(UUID.randomUUID()).build();
        entry.setRace(otherRace);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.createViolation(userId, raceId, createReq(entryId)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VIOLATION_ENTRY_RACE_MISMATCH);
    }

    @Test
    void create_raceNotFound() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createViolation(userId, raceId, createReq(entryId)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }

    // ── list ──

    @Test
    void list_buildsEntityLabel_andFilters() {
        RaceViolation v1 = RaceViolation.builder()
                .violationId(violationId).race(race).entry(entry).jockey(jockey)
                .infractionType(InfractionType.WHIP_USAGE).severity(SeverityLevel.MEDIUM)
                .status(ViolationStatus.PENDING).createdAt(OffsetDateTime.now()).build();
        RaceViolation v2 = RaceViolation.builder()
                .violationId(UUID.randomUUID()).race(race)
                .infractionType(InfractionType.BUMPING).severity(SeverityLevel.HIGH)
                .status(ViolationStatus.RESOLVED).createdAt(OffsetDateTime.now()).build();

        when(raceRepository.existsById(raceId)).thenReturn(true);
        when(violationRepository.findByRaceIdWithDetails(raceId)).thenReturn(List.of(v1, v2));

        // no filter
        List<ViolationListItemResponse> all = service.listViolations(raceId, null, null, null);
        assertThat(all).hasSize(2);
        ViolationListItemResponse i1 = all.stream()
                .filter(i -> i.getViolationId().equals(violationId)).findFirst().orElseThrow();
        assertThat(i1.getEntityLabel()).isEqualTo("Race R-07 / Thunderbolt / M. Reyes");
        // entry-less / jockey-less row omits the missing parts
        ViolationListItemResponse i2 = all.stream()
                .filter(i -> !i.getViolationId().equals(violationId)).findFirst().orElseThrow();
        assertThat(i2.getEntityLabel()).isEqualTo("Race R-07");

        // filter by status
        List<ViolationListItemResponse> pending = service.listViolations(raceId, ViolationStatus.PENDING, null, null);
        assertThat(pending).extracting(ViolationListItemResponse::getViolationId).containsExactly(violationId);

        // filter by infractionType
        List<ViolationListItemResponse> bumping = service.listViolations(raceId, null, null, InfractionType.BUMPING);
        assertThat(bumping).extracting(ViolationListItemResponse::getInfractionType)
                .containsExactly(InfractionType.BUMPING);

        // filter by severity
        List<ViolationListItemResponse> high = service.listViolations(raceId, null, SeverityLevel.HIGH, null);
        assertThat(high).hasSize(1);
        assertThat(high.get(0).getSeverity()).isEqualTo(SeverityLevel.HIGH);
    }

    @Test
    void list_raceNotFound() {
        when(raceRepository.existsById(raceId)).thenReturn(false);
        assertThatThrownBy(() -> service.listViolations(raceId, null, null, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }

    // ── detail ──

    @Test
    void detail_noRuling_rulingNull() {
        RaceViolation v = RaceViolation.builder()
                .violationId(violationId).race(race).entry(entry).jockey(jockey)
                .infractionType(InfractionType.WHIP_USAGE).status(ViolationStatus.PENDING)
                .reportedBy(reporter).build();
        when(violationRepository.findByIdWithDetails(violationId)).thenReturn(Optional.of(v));

        ViolationDetailResponse resp = service.getViolation(violationId);

        assertThat(resp.getRuling()).isNull();
        assertThat(resp.getRegulatoryText()).isEqualTo(v.getRegulatoryRef());
        assertThat(resp.getFootageUrl()).isNull();
        assertThat(resp.getReportedByUserId()).isEqualTo(userId);
    }

    @Test
    void detail_withRuling_andFootage_mapsValueAndUrl() {
        UUID footageId = UUID.randomUUID();
        Penalty penalty = Penalty.builder()
                .penaltyId(UUID.randomUUID())
                .penaltyType(PenaltyType.TIME_PENALTY).timePenaltyMs(2000L).build();
        RaceViolation v = RaceViolation.builder()
                .violationId(violationId).race(race).entry(entry).jockey(jockey)
                .infractionType(InfractionType.WHIP_USAGE).status(ViolationStatus.RESOLVED)
                .reportedBy(reporter).penalty(penalty).decisionType("PENALTY_APPLIED")
                .ruledBy(reporter).ruledAt(OffsetDateTime.now())
                .footageAttachmentId(footageId).regulatoryRef("Rule 142(b)").build();
        when(violationRepository.findByIdWithDetails(violationId)).thenReturn(Optional.of(v));

        ViolationDetailResponse resp = service.getViolation(violationId);

        assertThat(resp.getRuling()).isNotNull();
        assertThat(resp.getRuling().getDecisionType()).isEqualTo("PENALTY_APPLIED");
        assertThat(resp.getRuling().getPenaltyType()).isEqualTo(PenaltyType.TIME_PENALTY);
        assertThat(resp.getRuling().getPenaltyValue()).isEqualTo("+2.0s");
        assertThat(resp.getRuling().getRuledByName()).isEqualTo("A. Khan");
        assertThat(resp.getFootageUrl()).isEqualTo("/api/v1/files/" + footageId);
    }

    @Test
    void detail_notFound() {
        when(violationRepository.findByIdWithDetails(violationId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getViolation(violationId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VIOLATION_NOT_FOUND);
    }

    // ── ruling ──

    @Test
    void ruling_penaltyApplied_createsPenalty_resolves() {
        RaceViolation v = RaceViolation.builder()
                .violationId(violationId).race(race).entry(entry)
                .infractionType(InfractionType.WHIP_USAGE).status(ViolationStatus.PENDING).build();
        when(violationRepository.findByIdWithDetails(violationId)).thenReturn(Optional.of(v));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(reporter));
        UUID penaltyId = UUID.randomUUID();
        when(penaltyRepository.save(any(Penalty.class))).thenAnswer(inv -> {
            Penalty p = inv.getArgument(0);
            p.setPenaltyId(penaltyId);
            return p;
        });
        when(violationRepository.save(any(RaceViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        RulingRequest req = new RulingRequest("PENALTY_APPLIED", PenaltyType.TIME_PENALTY, 2000L, null, "2s penalty.");
        RulingResponse resp = service.recordRuling(userId, violationId, req);

        ArgumentCaptor<Penalty> pc = ArgumentCaptor.forClass(Penalty.class);
        verify(penaltyRepository).save(pc.capture());
        Penalty createdPenalty = pc.getValue();
        assertThat(createdPenalty.getPenaltyType()).isEqualTo(PenaltyType.TIME_PENALTY);
        assertThat(createdPenalty.getTimePenaltyMs()).isEqualTo(2000L);
        assertThat(createdPenalty.getReason()).isEqualTo("2s penalty.");
        assertThat(createdPenalty.getIssuedBy()).isEqualTo(reporter);
        assertThat(createdPenalty.getStatus()).isEqualTo(PenaltyStatus.ISSUED);
        assertThat(createdPenalty.getRace()).isEqualTo(race);
        assertThat(createdPenalty.getEntry()).isEqualTo(entry);

        assertThat(resp.getStatus()).isEqualTo(ViolationStatus.RESOLVED);
        assertThat(resp.getPenaltyId()).isEqualTo(penaltyId);
        assertThat(resp.getDecisionType()).isEqualTo("PENALTY_APPLIED");
        assertThat(resp.getTimePenaltyMs()).isEqualTo(2000L);
        assertThat(resp.getRuledByUserId()).isEqualTo(userId);
        assertThat(resp.getRuledAt()).isNotNull();

        assertThat(v.getStatus()).isEqualTo(ViolationStatus.RESOLVED);
        assertThat(v.getPenalty()).isEqualTo(createdPenalty);
        assertThat(v.getRuledBy()).isEqualTo(reporter);
    }

    @Test
    void ruling_dismissed_noPenalty_dismisses() {
        RaceViolation v = RaceViolation.builder()
                .violationId(violationId).race(race).entry(entry)
                .infractionType(InfractionType.WHIP_USAGE).status(ViolationStatus.UNDER_REVIEW).build();
        when(violationRepository.findByIdWithDetails(violationId)).thenReturn(Optional.of(v));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(reporter));
        when(violationRepository.save(any(RaceViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        RulingRequest req = new RulingRequest("DISMISSED", null, null, null, "No infraction found.");
        RulingResponse resp = service.recordRuling(userId, violationId, req);

        verify(penaltyRepository, never()).save(any());
        assertThat(resp.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        assertThat(resp.getPenaltyId()).isNull();
        assertThat(v.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        assertThat(v.getDecisionType()).isEqualTo("DISMISSED");
    }

    @Test
    void ruling_nullPrincipal_unauthenticated() {
        RulingRequest req = new RulingRequest("DISMISSED", null, null, null, null);
        assertThatThrownBy(() -> service.recordRuling(null, violationId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void ruling_alreadyResolved_guarded() {
        RaceViolation v = RaceViolation.builder()
                .violationId(violationId).race(race).status(ViolationStatus.RESOLVED).build();
        when(violationRepository.findByIdWithDetails(violationId)).thenReturn(Optional.of(v));

        RulingRequest req = new RulingRequest("DISMISSED", null, null, null, null);
        assertThatThrownBy(() -> service.recordRuling(userId, violationId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VIOLATION_ALREADY_RULED);
    }

    @Test
    void ruling_violationNotFound() {
        when(violationRepository.findByIdWithDetails(violationId)).thenReturn(Optional.empty());
        RulingRequest req = new RulingRequest("DISMISSED", null, null, null, null);
        assertThatThrownBy(() -> service.recordRuling(userId, violationId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VIOLATION_NOT_FOUND);
    }

    // ── CSV export ──

    @Test
    void exportCsv_headerAndRows_escapesCommas() {
        RaceViolation v = RaceViolation.builder()
                .violationId(violationId).race(race)
                .infractionType(InfractionType.WHIP_USAGE).severity(SeverityLevel.MEDIUM)
                .turnNo(3).raceTimeOffsetMs(84000L).status(ViolationStatus.PENDING)
                .remarks("Excessive whip use, home straight").build();
        when(raceRepository.existsById(raceId)).thenReturn(true);
        when(violationRepository.findByRaceIdWithDetails(raceId)).thenReturn(List.of(v));

        String csv = service.exportCsv(raceId);

        assertThat(csv).startsWith("violationId,infractionType,severity,turnNo,raceTimeOffsetMs,status,remarks\n");
        assertThat(csv).contains(violationId + ",WHIP_USAGE,MEDIUM,3,84000,PENDING,");
        // remarks contains a comma -> quoted
        assertThat(csv).contains("\"Excessive whip use, home straight\"");
    }

    @Test
    void exportCsv_raceNotFound() {
        when(raceRepository.existsById(raceId)).thenReturn(false);
        assertThatThrownBy(() -> service.exportCsv(raceId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }
}
