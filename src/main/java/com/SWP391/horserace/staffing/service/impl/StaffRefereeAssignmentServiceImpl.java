package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.assignments.entity.PanelRole;
import com.SWP391.horserace.assignments.entity.RefereeAssignment;
import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.dto.AssignRefereeRequest;
import com.SWP391.horserace.staffing.dto.RaceAssignmentFilterRequest;
import com.SWP391.horserace.staffing.dto.RaceAssignmentResponse;
import com.SWP391.horserace.staffing.dto.ReassignRefereeRequest;
import com.SWP391.horserace.staffing.dto.RefereeAssignmentResponse;
import com.SWP391.horserace.staffing.dto.StaffingDashboardResponse;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.staffing.service.StaffRefereeAssignmentService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffRefereeAssignmentServiceImpl implements StaffRefereeAssignmentService {

    private static final RefereeAssignmentStatus REVOKED = RefereeAssignmentStatus.REVOKED;
    private static final RefereeAssignmentStatus ASSIGNED = RefereeAssignmentStatus.ASSIGNED;
    private static final String REFEREE_ROLE_CODE = "RACE_REFEREE";

    private final RefereeAssignmentRepository refereeAssignmentRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // DASHBOARD
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public StaffingDashboardResponse getDashboard() {
        long totalScheduledRaces = raceRepository.count();
        long assignedRaces = refereeAssignmentRepository.countDistinctAssignedRaces();
        long unassignedRaces = Math.max(0, totalScheduledRaces - assignedRaces);

        long availableReferees = userRepository.findAll(
                (root, query, cb) -> {
                    var roleJoin = root.join("role");
                    return cb.and(
                            cb.equal(roleJoin.get("roleCode"), REFEREE_ROLE_CODE),
                            cb.isFalse(root.get("deleted")),
                            cb.equal(root.get("status"), com.SWP391.horserace.users.entity.UserStatus.ACTIVE)
                    );
                }
        ).size();

        return StaffingDashboardResponse.builder()
                .totalScheduledRaces(totalScheduledRaces)
                .assignedReferees(assignedRaces)
                .unassignedRaces(unassignedRaces)
                .availableReferees(availableReferees)
                .build();
    }

    // =========================================================================
    // GET RACE ASSIGNMENTS  (Figma table)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<RaceAssignmentResponse> getRaceAssignments(RaceAssignmentFilterRequest filter) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(filter.getSortDir())
                        ? Sort.Direction.DESC : Sort.Direction.ASC,
                filter.getSortBy()
        );
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<Race> racePage = raceRepository.findAll(pageable);

        List<UUID> raceIds = racePage.getContent().stream()
                .map(Race::getRaceId)
                .toList();

        Map<UUID, RefereeAssignment> activeAssignments = raceIds.stream()
                .flatMap(raceId -> refereeAssignmentRepository
                        .findByRace_RaceIdAndStatusNot(raceId, REVOKED).stream())
                .collect(Collectors.toMap(
                        ra -> ra.getRace().getRaceId(),
                        ra -> ra,
                        (a, b) -> a
                ));

        List<RaceAssignmentResponse> responses = racePage.getContent().stream()
                .map(race -> buildRaceAssignmentResponse(race, activeAssignments.get(race.getRaceId())))
                .filter(response -> matchesFilters(response, filter))
                .toList();

        return new PageImpl<>(responses, pageable, racePage.getTotalElements());
    }

    // =========================================================================
    // ASSIGN REFEREE
    // =========================================================================
    @Override
    @Transactional
    public RefereeAssignmentResponse assignReferee(AssignRefereeRequest request, UUID currentUserId) {
        Race race = raceRepository.findById(request.getRaceId())
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        User referee = loadReferee(request.getRefereeUserId());

        if (refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(
                request.getRaceId(), request.getRefereeUserId(), REVOKED)) {
            throw new AppException(ErrorCode.REFEREE_ALREADY_ASSIGNED);
        }

        User createdBy = currentUserId != null
                ? userRepository.findByUserIdAndDeletedFalse(currentUserId).orElse(null)
                : null;

        RefereeAssignment assignment = RefereeAssignment.builder()
                .race(race)
                .referee(referee)
                .panelRole(toPanelRole(request.getPanelRole()))
                .status(ASSIGNED)
                .assignedAt(OffsetDateTime.now())
                .createdBy(createdBy)
                .build();

        return mapToAssignmentResponse(refereeAssignmentRepository.save(assignment));
    }

    // =========================================================================
    // REASSIGN REFEREE
    // =========================================================================
    @Override
    @Transactional
    public RefereeAssignmentResponse reassignReferee(UUID refAssignmentId,
                                                      ReassignRefereeRequest request,
                                                      UUID currentUserId) {
        RefereeAssignment existing = refereeAssignmentRepository
                .findByRefAssignmentIdAndStatusNot(refAssignmentId, REVOKED)
                .orElseThrow(() -> new AppException(ErrorCode.REFEREE_ASSIGNMENT_NOT_FOUND));

        User newReferee = loadReferee(request.getNewRefereeUserId());

        UUID raceId = existing.getRace().getRaceId();
        if (refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(
                raceId, request.getNewRefereeUserId(), REVOKED)) {
            throw new AppException(ErrorCode.REFEREE_ALREADY_ASSIGNED);
        }

        existing.setStatus(REVOKED);
        refereeAssignmentRepository.save(existing);

        User createdBy = currentUserId != null
                ? userRepository.findByUserIdAndDeletedFalse(currentUserId).orElse(null)
                : null;

        PanelRole panelRole = request.getPanelRole() != null
                ? toPanelRole(request.getPanelRole())
                : existing.getPanelRole();

        RefereeAssignment newAssignment = RefereeAssignment.builder()
                .race(existing.getRace())
                .referee(newReferee)
                .panelRole(panelRole)
                .status(ASSIGNED)
                .assignedAt(OffsetDateTime.now())
                .createdBy(createdBy)
                .build();

        return mapToAssignmentResponse(refereeAssignmentRepository.save(newAssignment));
    }

    // =========================================================================
    // REMOVE ASSIGNMENT
    // =========================================================================
    @Override
    @Transactional
    public void removeAssignment(UUID refAssignmentId) {
        RefereeAssignment assignment = refereeAssignmentRepository.findById(refAssignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.REFEREE_ASSIGNMENT_NOT_FOUND));

        if (REVOKED == assignment.getStatus()) {
            throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_REVOKED);
        }

        assignment.setStatus(REVOKED);
        refereeAssignmentRepository.save(assignment);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private User loadReferee(UUID userId) {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        Role role = user.getRole();
        if (role == null || !REFEREE_ROLE_CODE.equals(role.getRoleCode())) {
            throw new AppException(ErrorCode.STAFF_NOT_REFEREE);
        }
        return user;
    }

    /** Convert a client-supplied panel-role string to the enum (null/blank → null). */
    private PanelRole toPanelRole(String raw) {
        return (raw == null || raw.isBlank()) ? null : PanelRole.valueOf(raw.trim().toUpperCase());
    }

    private RefereeAssignmentResponse mapToAssignmentResponse(RefereeAssignment ra) {
        Race race = ra.getRace();
        User referee = ra.getReferee();
        User createdBy = ra.getCreatedBy();

        return RefereeAssignmentResponse.builder()
                .refAssignmentId(ra.getRefAssignmentId())
                .raceId(race != null ? race.getRaceId() : null)
                .raceName(race != null ? race.getName() : null)
                .raceCode(race != null ? race.getRaceCode() : null)
                .scheduledStartAt(race != null ? race.getScheduledStartAt() : null)
                .refereeUserId(referee != null ? referee.getUserId() : null)
                .refereeName(referee != null ? referee.getFullName() : null)
                .refereeAvatarUrl(referee != null ? referee.getAvatarUrl() : null)
                .panelRole(ra.getPanelRole() != null ? ra.getPanelRole().name() : null)
                .status(ra.getStatus() != null ? ra.getStatus().name() : null)
                .assignedAt(ra.getAssignedAt())
                .createdAt(ra.getCreatedAt())
                .createdByUserId(createdBy != null ? createdBy.getUserId() : null)
                .createdByName(createdBy != null ? createdBy.getFullName() : null)
                .build();
    }

    private RaceAssignmentResponse buildRaceAssignmentResponse(Race race, RefereeAssignment assignment) {
        RaceAssignmentResponse.RaceAssignmentResponseBuilder builder = RaceAssignmentResponse.builder()
                .raceId(race.getRaceId())
                .raceName(race.getName())
                .raceCode(race.getRaceCode())
                .raceStatus(race.getStatus() != null ? race.getStatus().name() : null)
                .scheduledStartAt(race.getScheduledStartAt());

        if (assignment != null) {
            User referee = assignment.getReferee();
            builder.refAssignmentId(assignment.getRefAssignmentId())
                    .refereeUserId(referee != null ? referee.getUserId() : null)
                    .refereeName(referee != null ? referee.getFullName() : null)
                    .refereeAvatarUrl(referee != null ? referee.getAvatarUrl() : null)
                    .panelRole(assignment.getPanelRole() != null ? assignment.getPanelRole().name() : null)
                    .assignmentStatus(assignment.getStatus() != null ? assignment.getStatus().name() : null);
        } else {
            builder.assignmentStatus("UNASSIGNED");
        }

        return builder.build();
    }

    private boolean matchesFilters(RaceAssignmentResponse response, RaceAssignmentFilterRequest filter) {
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            String search = filter.getSearch().toLowerCase();
            boolean matches = false;
            if (response.getRaceName() != null && response.getRaceName().toLowerCase().contains(search)) {
                matches = true;
            }
            if (response.getRaceCode() != null && response.getRaceCode().toLowerCase().contains(search)) {
                matches = true;
            }
            if (response.getRefereeName() != null && response.getRefereeName().toLowerCase().contains(search)) {
                matches = true;
            }
            if (!matches) return false;
        }

        if (filter.getRaceStatus() != null && !filter.getRaceStatus().isBlank()) {
            if (!filter.getRaceStatus().equalsIgnoreCase(response.getRaceStatus())) {
                return false;
            }
        }

        if (filter.getAssignmentStatus() != null && !filter.getAssignmentStatus().isBlank()) {
            if (!filter.getAssignmentStatus().equalsIgnoreCase(response.getAssignmentStatus())) {
                return false;
            }
        }

        return true;
    }
}
