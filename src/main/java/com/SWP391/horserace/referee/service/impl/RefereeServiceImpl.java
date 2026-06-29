package com.SWP391.horserace.referee.service.impl;

import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.referee.dto.CreateReportRequest;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;
import com.SWP391.horserace.referee.dto.ReportFilterRequest;
import com.SWP391.horserace.referee.dto.ReportResponse;
import com.SWP391.horserace.referee.dto.UpdateReportRequest;
import com.SWP391.horserace.referee.service.RefereeService;
import com.SWP391.horserace.reports.entity.RefereeReport;
import com.SWP391.horserace.reports.entity.ReportStatus;
import com.SWP391.horserace.reports.entity.ReportType;
import com.SWP391.horserace.reports.repository.RefereeReportRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefereeServiceImpl implements RefereeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RefereeReportRepository refereeReportRepository;
    private final HorseRepository horseRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;

    // ── horse health check ──

    @Override
    @Transactional
    public MedicalStatusResponse recordHealthCheck(UUID currentUserId, UUID horseId, HealthCheckRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Horse horse = horseRepository.findByHorseIdAndDeletedFalse(horseId)
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));

        horse.setHealthStatus(request.healthStatus());
        horse.setLastHealthCheckAt(OffsetDateTime.now());
        if (request.note() != null) {
            horse.setMedicalNote(request.note());
        }

        return mapToMedicalStatus(horseRepository.save(horse));
    }

    // ── reports ──

    @Override
    @Transactional
    public ReportResponse createReport(UUID currentUserId, CreateReportRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = raceRepository.findByRaceIdAndDeletedFalse(request.raceId())
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        User author = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        RefereeReport report = RefereeReport.builder()
                .race(race)
                .author(author)
                .reportType(request.reportType() != null ? request.reportType() : ReportType.VIOLATION)
                .summary(request.summary())
                .decision(request.decision())
                .severityLevel(request.severityLevel())
                .reportStatus(ReportStatus.DRAFT)
                .build();

        return mapToResponse(refereeReportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> listReports(ReportFilterRequest filter) {
        Specification<RefereeReport> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getRaceId() != null) {
                predicates.add(cb.equal(root.get("race").get("raceId"), filter.getRaceId()));
            }
            if (filter.getReportType() != null) {
                predicates.add(cb.equal(root.get("reportType"), filter.getReportType()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("reportStatus"), filter.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return refereeReportRepository.findAll(spec, buildPageable(filter)).map(this::mapToResponse);
    }

    @Override
    @Transactional
    public ReportResponse updateReport(UUID currentUserId, UUID reportId, UpdateReportRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        RefereeReport report = loadReport(reportId);

        if (report.getReportStatus() != ReportStatus.DRAFT) {
            throw new AppException(ErrorCode.REPORT_INVALID_STATUS);
        }

        // Partial update: apply only non-null fields.
        if (request.reportType() != null) report.setReportType(request.reportType());
        if (request.summary() != null) report.setSummary(request.summary());
        if (request.decision() != null) report.setDecision(request.decision());
        if (request.severityLevel() != null) report.setSeverityLevel(request.severityLevel());

        return mapToResponse(refereeReportRepository.save(report));
    }

    @Override
    @Transactional
    public ReportResponse submitReport(UUID currentUserId, UUID reportId) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        RefereeReport report = loadReport(reportId);

        if (report.getReportStatus() != ReportStatus.DRAFT) {
            throw new AppException(ErrorCode.REPORT_INVALID_STATUS);
        }

        report.setReportStatus(ReportStatus.SUBMITTED);
        report.setSubmittedAt(OffsetDateTime.now());

        return mapToResponse(refereeReportRepository.save(report));
    }

    // ── helpers ──

    private RefereeReport loadReport(UUID reportId) {
        return refereeReportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
    }

    private Pageable buildPageable(ReportFilterRequest f) {
        int page = (f.getPage() != null && f.getPage() >= 0) ? f.getPage() : 0;
        int size = (f.getSize() != null && f.getSize() > 0) ? Math.min(f.getSize(), MAX_PAGE_SIZE) : 10;
        String field = switch (f.getSortBy() != null ? f.getSortBy().trim().toLowerCase() : "createdat") {
            case "submittedat" -> "submittedAt";
            case "severitylevel" -> "severityLevel";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(f.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private MedicalStatusResponse mapToMedicalStatus(Horse h) {
        return MedicalStatusResponse.builder()
                .horseId(h.getHorseId())
                .horseName(h.getName())
                .healthStatus(h.getHealthStatus())
                .lastHealthCheckAt(h.getLastHealthCheckAt())
                .medicalNote(h.getMedicalNote())
                .build();
    }

    private ReportResponse mapToResponse(RefereeReport r) {
        Race race = r.getRace();
        User author = r.getAuthor();
        return ReportResponse.builder()
                .reportId(r.getReportId())
                .raceId(race != null ? race.getRaceId() : null)
                .authorUserId(author != null ? author.getUserId() : null)
                .authorName(author != null ? author.getFullName() : null)
                .reportType(r.getReportType())
                .summary(r.getSummary())
                .decision(r.getDecision())
                .severityLevel(r.getSeverityLevel())
                .reportStatus(r.getReportStatus())
                .submittedAt(r.getSubmittedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
