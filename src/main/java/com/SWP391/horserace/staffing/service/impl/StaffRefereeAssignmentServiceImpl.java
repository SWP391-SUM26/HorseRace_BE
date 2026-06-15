package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.assignments.entity.RefereeAssignment;
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

    private static final String REVOKED = "REVOKED";
    private static final String ASSIGNED = "ASSIGNED";
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
        // Total scheduled races (all statuses that are not CANCELLED or FINISHED).
        long totalScheduledRaces = raceRepository.count();

        // Races with at least one active assignment.
        long assignedRaces = refereeAssignmentRepository.countDistinctAssignedRaces();

        // Unassigned = total - assigned.
        long unassignedRaces = Math.max(0, totalScheduledRaces - assignedRaces);

        // Active referees.
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

        // Get all races with pagination.
        Page<Race> racePage = raceRepository.findAll(pageable);

        // Collect raceIds from current page.
        List<UUID> raceIds = racePage.getContent().stream()
                .map(Race::getRaceId)
                .toList();

        // Load all active assignments for these races.
        Map<UUID, RefereeAssignment> activeAssignments = raceIds.stream()
                .flatMap(raceId -> refereeAssignmentRepository
                        .findByRace_RaceIdAndStatusNot(raceId, REVOKED).stream())
                .collect(Collectors.toMap(
                        ra -> ra.getRace().getRaceId(),
                        ra -> ra,
                        (a, b) -> a  // if multiple assignments, take first
                ));

        // Build response list.
        List<RaceAssignmentResponse> responses = racePage.getContent().stream()
                .map(race -> {
                    RefereeAssignment assignment = activeAssignments.get(race.getRaceId());
                    return buildRaceAssignmentResponse(race, assignment);
                })
                .filter(response -> matchesFilters(response, filter))
                .toList();

        return new PageImpl<>(responses, pageable, racePage.getTotalElements());
    }

    // =========================================================================
    // ASSIGN REFEREE  (task 148)
    // =========================================================================
    @Override
    @Transactional
    public RefereeAssignmentResponse assignReferee(AssignRefereeRequest request, UUID currentUserId) {
        // Validate race exists.
        Race race = raceRepository.findById(request.getRaceId())
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        // Validate referee exists and has RACE_REFEREE role.
        User referee = loadReferee(request.getRefereeUserId());

        // Validate no duplicate assignment.
        if (refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(
                request.getRaceId(), request.getRefereeUserId(), REVOKED)) {
            throw new AppException(ErrorCode.REFEREE_ALREADY_ASSIGNED);
        }

        // Resolve the creating user (nullable in DEV mode).
        User createdBy = currentUserId != null
                ? userRepository.findByUserIdAndDeletedFalse(currentUserId).orElse(null)
                : null;

        RefereeAssignment assignment = RefereeAssignment.builder()
                .race(race)
                .referee(referee)
                .panelRole(request.getPanelRole())
                .status(ASSIGNED)
                .assignedAt(OffsetDateTime.now())
                .createdBy(createdBy)
                .build();

        RefereeAssignment saved = refereeAssignmentRepository.save(assignment);
        return mapToAssignmentResponse(saved);
    }

    // =========================================================================
    // REASSIGN REFEREE  (task 150)
    // =========================================================================
    @Override
    @Transactional
    public RefereeAssignmentResponse reassignReferee(UUID refAssignmentId,
                                                      ReassignRefereeRequest request,
                                                      UUID currentUserId) {
        // Find the existing active assignment.
        RefereeAssignment existing = refereeAssignmentRepository
                .findByRefAssignmentIdAndStatusNot(refAssignmentId, REVOKED)
                .orElseThrow(() -> new AppException(ErrorCode.REFEREE_ASSIGNMENT_NOT_FOUND));

        // Validate new referee exists and has RACE_REFEREE role.
        User newReferee = loadReferee(request.getNewRefereeUserId());

        // Check no duplicate assignment for the new referee on the same race.
        UUID raceId = existing.getRace().getRaceId();
        if (refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(
                raceId, request.getNewRefereeUserId(), REVOKED)) {
            throw new AppException(ErrorCode.REFEREE_ALREADY_ASSIGNED);
        }

        // Revoke the old assignment.
        existing.setStatus(REVOKED);
        refereeAssignmentRepository.save(existing);

        // Resolve the creating user (nullable in DEV mode).
        User createdBy = currentUserId != null
                ? userRepository.findByUserIdAndDeletedFalse(currentUserId).orElse(null)
                : null;

        // Create a new assignment with the new referee.
        RefereeAssignment newAssignment = RefereeAssignment.builder()
                .race(existing.getRace())
                .referee(newReferee)
                .panelRole(request.getPanelRole() != null ? request.getPanelRole() : existing.getPanelRole())
                .status(ASSIGNED)
                .assignedAt(OffsetDateTime.now())
                .createdBy(createdBy)
                .build();

        RefereeAssignment saved = refereeAssignmentRepository.save(newAssignment);
        return mapToAssignmentResponse(saved);
    }

    // =========================================================================
    // REMOVE ASSIGNMENT  (task 152)
    // =========================================================================
    @Override
    @Transactional
    public void removeAssignment(UUID refAssignmentId) {
        RefereeAssignment assignment = refereeAssignmentRepository.findById(refAssignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.REFEREE_ASSIGNMENT_NOT_FOUND));

        if (REVOKED.equals(assignment.getStatus())) {
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
                .panelRole(ra.getPanelRole())
                .status(ra.getStatus())
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
                .raceStatus(race.getStatus())
                .scheduledStartAt(race.getScheduledStartAt());

        if (assignment != null) {
            User referee = assignment.getReferee();
            builder.refAssignmentId(assignment.getRefAssignmentId())
                    .refereeUserId(referee != null ? referee.getUserId() : null)
                    .refereeName(referee != null ? referee.getFullName() : null)
                    .refereeAvatarUrl(referee != null ? referee.getAvatarUrl() : null)
                    .panelRole(assignment.getPanelRole())
                    .assignmentStatus(assignment.getStatus());
        } else {
            builder.assignmentStatus("UNASSIGNED");
        }

        return builder.build();
    }

    private boolean matchesFilters(RaceAssignmentResponse response, RaceAssignmentFilterRequest filter) {
        // Search filter.
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

        // Race status filter.
        if (filter.getRaceStatus() != null && !filter.getRaceStatus().isBlank()) {
            if (!filter.getRaceStatus().equalsIgnoreCase(response.getRaceStatus())) {
                return false;
            }
        }

        // Assignment status filter.
        if (filter.getAssignmentStatus() != null && !filter.getAssignmentStatus().isBlank()) {
            if (!filter.getAssignmentStatus().equalsIgnoreCase(response.getAssignmentStatus())) {
                return false;
            }
        }

        return true;
    }
}
