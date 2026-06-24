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
import com.SWP391.horserace.violations.service.ViolationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ViolationServiceImpl implements ViolationService {

    /** decision_type value that triggers a penalty being created + linked. */
    private static final String DECISION_PENALTY_APPLIED = "PENALTY_APPLIED";
    /** decision_type value that dismisses the violation. */
    private static final String DECISION_DISMISSED = "DISMISSED";

    private final RaceViolationRepository violationRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;
    private final PenaltyRepository penaltyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ViolationDetailResponse createViolation(UUID currentUserId, UUID raceId, CreateViolationRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        RaceEntry entry = null;
        if (request.entryId() != null) {
            entry = raceEntryRepository.findByIdWithDetails(request.entryId())
                    .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));
            if (entry.getRace() == null || !raceId.equals(entry.getRace().getRaceId())) {
                throw new AppException(ErrorCode.VIOLATION_ENTRY_RACE_MISMATCH);
            }
        }

        User reporter = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Resolve the riding jockey from the entry (if any) so the row records who offended.
        User jockey = null;
        if (entry != null) {
            jockey = jockeyAssignmentRepository.findAcceptedByEntryId(entry.getEntryId())
                    .map(JockeyAssignment::getJockey)
                    .orElse(null);
        }

        RaceViolation violation = RaceViolation.builder()
                .race(race)
                .entry(entry)
                .jockey(jockey)
                .infractionType(request.infractionType())
                .severity(request.severity())
                .turnNo(request.turnNo())
                .raceTimeOffsetMs(request.raceTimeOffsetMs())
                .remarks(request.remarks())
                .regulatoryRef(request.regulatoryRef())
                .footageAttachmentId(request.footageAttachmentId())
                .status(ViolationStatus.PENDING)
                .reportedBy(reporter)
                .build();

        RaceViolation saved = violationRepository.save(violation);
        return toDetail(saved, jockey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViolationListItemResponse> listViolations(UUID raceId, ViolationStatus status,
                                                          SeverityLevel severity, InfractionType infractionType) {
        if (!raceRepository.existsById(raceId)) {
            throw new AppException(ErrorCode.RACE_NOT_FOUND);
        }

        return violationRepository.findByRaceIdWithDetails(raceId).stream()
                .filter(v -> status == null || v.getStatus() == status)
                .filter(v -> severity == null || v.getSeverity() == severity)
                .filter(v -> infractionType == null || v.getInfractionType() == infractionType)
                .map(this::toListItem)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ViolationDetailResponse getViolation(UUID violationId) {
        RaceViolation violation = violationRepository.findByIdWithDetails(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.VIOLATION_NOT_FOUND));
        return toDetail(violation, violation.getJockey());
    }

    @Override
    @Transactional
    public RulingResponse recordRuling(UUID currentUserId, UUID violationId, RulingRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        RaceViolation violation = violationRepository.findByIdWithDetails(violationId)
                .orElseThrow(() -> new AppException(ErrorCode.VIOLATION_NOT_FOUND));

        // Guard against re-ruling an already-resolved/dismissed violation.
        if (violation.getStatus() == ViolationStatus.RESOLVED || violation.getStatus() == ViolationStatus.DISMISSED) {
            throw new AppException(ErrorCode.VIOLATION_ALREADY_RULED);
        }

        User ruler = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String decisionType = request.decisionType();
        Penalty penalty = null;

        if (DECISION_PENALTY_APPLIED.equals(decisionType)) {
            penalty = Penalty.builder()
                    .race(violation.getRace())
                    .entry(violation.getEntry())
                    .penaltyType(request.penaltyType())
                    .timePenaltyMs(request.timePenaltyMs())
                    .fineAmount(request.fineAmount())
                    .reason(request.rulingNotes())
                    .issuedBy(ruler)
                    .status(PenaltyStatus.ISSUED)
                    .build();
            penalty = penaltyRepository.save(penalty);
            violation.setPenalty(penalty);
        }

        violation.setStatus(DECISION_DISMISSED.equals(decisionType)
                ? ViolationStatus.DISMISSED
                : ViolationStatus.RESOLVED);
        violation.setDecisionType(decisionType);
        violation.setRulingNotes(request.rulingNotes());
        violation.setRuledBy(ruler);
        violation.setRuledAt(OffsetDateTime.now());

        RaceViolation saved = violationRepository.save(violation);

        return RulingResponse.builder()
                .violationId(saved.getViolationId())
                .status(saved.getStatus())
                .penaltyId(penalty != null ? penalty.getPenaltyId() : null)
                .decisionType(decisionType)
                .penaltyType(request.penaltyType())
                .timePenaltyMs(request.timePenaltyMs())
                .ruledByUserId(ruler.getUserId())
                .ruledAt(saved.getRuledAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new AppException(ErrorCode.RACE_NOT_FOUND);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("violationId,infractionType,severity,turnNo,raceTimeOffsetMs,status,remarks\n");
        for (RaceViolation v : violationRepository.findByRaceIdWithDetails(raceId)) {
            sb.append(csv(v.getViolationId() != null ? v.getViolationId().toString() : null)).append(',')
              .append(csv(v.getInfractionType() != null ? v.getInfractionType().name() : null)).append(',')
              .append(csv(v.getSeverity() != null ? v.getSeverity().name() : null)).append(',')
              .append(csv(v.getTurnNo() != null ? v.getTurnNo().toString() : null)).append(',')
              .append(csv(v.getRaceTimeOffsetMs() != null ? v.getRaceTimeOffsetMs().toString() : null)).append(',')
              .append(csv(v.getStatus() != null ? v.getStatus().name() : null)).append(',')
              .append(csv(v.getRemarks())).append('\n');
        }
        return sb.toString();
    }

    // ── mapping helpers ──

    private ViolationListItemResponse toListItem(RaceViolation v) {
        return ViolationListItemResponse.builder()
                .violationId(v.getViolationId())
                .entityLabel(buildEntityLabel(v))
                .infractionType(v.getInfractionType())
                .severity(v.getSeverity())
                .turnNo(v.getTurnNo())
                .raceTimeOffsetMs(v.getRaceTimeOffsetMs())
                .status(v.getStatus())
                .createdAt(v.getCreatedAt())
                .build();
    }

    private ViolationDetailResponse toDetail(RaceViolation v, User jockey) {
        Horse horse = horseOf(v.getEntry());
        Penalty penalty = v.getPenalty();

        ViolationDetailResponse.Ruling ruling = null;
        if (v.getRuledAt() != null || v.getDecisionType() != null) {
            ruling = ViolationDetailResponse.Ruling.builder()
                    .decisionType(v.getDecisionType())
                    .penaltyType(penalty != null ? penalty.getPenaltyType() : null)
                    .penaltyValue(penaltyValue(penalty))
                    .ruledByName(v.getRuledBy() != null ? v.getRuledBy().getFullName() : null)
                    .ruledAt(v.getRuledAt())
                    .build();
        }

        return ViolationDetailResponse.builder()
                .violationId(v.getViolationId())
                .raceId(v.getRace() != null ? v.getRace().getRaceId() : null)
                .entryId(v.getEntry() != null ? v.getEntry().getEntryId() : null)
                .horseName(horse != null ? horse.getName() : null)
                .jockeyName(jockey != null ? jockey.getFullName() : null)
                .infractionType(v.getInfractionType())
                .severity(v.getSeverity())
                .turnNo(v.getTurnNo())
                .raceTimeOffsetMs(v.getRaceTimeOffsetMs())
                .remarks(v.getRemarks())
                .regulatoryRef(v.getRegulatoryRef())
                .regulatoryText(v.getRegulatoryRef())
                .footageAttachmentId(v.getFootageAttachmentId())
                .footageUrl(v.getFootageAttachmentId() != null
                        ? "/api/v1/files/" + v.getFootageAttachmentId()
                        : null)
                .status(v.getStatus())
                .reportedByUserId(v.getReportedBy() != null ? v.getReportedBy().getUserId() : null)
                .createdAt(v.getCreatedAt())
                .ruling(ruling)
                .build();
    }

    /** "Race {raceCode} / {horseName} / {jockeyName}" — missing parts omitted. */
    private String buildEntityLabel(RaceViolation v) {
        StringBuilder sb = new StringBuilder();
        if (v.getRace() != null && v.getRace().getRaceCode() != null) {
            sb.append("Race ").append(v.getRace().getRaceCode());
        }
        Horse horse = horseOf(v.getEntry());
        if (horse != null && horse.getName() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(horse.getName());
        }
        if (v.getJockey() != null && v.getJockey().getFullName() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(v.getJockey().getFullName());
        }
        return sb.toString();
    }

    private Horse horseOf(RaceEntry entry) {
        if (entry == null || entry.getRegistration() == null) {
            return null;
        }
        return entry.getRegistration().getHorse();
    }

    /** Human-readable penalty value, e.g. "+2.0s" for a time penalty or "$500" for a fine. */
    private String penaltyValue(Penalty penalty) {
        if (penalty == null) {
            return null;
        }
        if (penalty.getPenaltyType() == PenaltyType.TIME_PENALTY && penalty.getTimePenaltyMs() != null) {
            return String.format("+%.1fs", penalty.getTimePenaltyMs() / 1000.0);
        }
        if (penalty.getPenaltyType() == PenaltyType.FINE && penalty.getFineAmount() != null) {
            return "$" + penalty.getFineAmount().stripTrailingZeros().toPlainString();
        }
        return null;
    }

    /** Minimal RFC-4180 CSV escaping: quote when the value contains comma, quote or newline. */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
