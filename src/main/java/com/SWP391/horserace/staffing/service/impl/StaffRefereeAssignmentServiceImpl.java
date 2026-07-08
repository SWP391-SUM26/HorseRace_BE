package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.assignments.entity.PanelRole;
import com.SWP391.horserace.assignments.entity.RefereeAssignment;
import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.notifications.service.NotificationService;
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
    private static final RefereeAssignmentStatus CONFIRMED = RefereeAssignmentStatus.CONFIRMED;
    private static final RefereeAssignmentStatus DECLINED = RefereeAssignmentStatus.DECLINED;
    private static final String REFEREE_ROLE_CODE = "RACE_REFEREE";

    private final RefereeAssignmentRepository refereeAssignmentRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /** ± window (hours) around a race's scheduledStartAt within which a referee is considered busy. */
    @org.springframework.beans.factory.annotation.Value("${app.staffing.referee-conflict-window-hours:2}")
    private long conflictWindowHours;

    /**
     * Rejects assigning {@code refereeUserId} to {@code race} if the referee already officiates
     * another race whose scheduledStartAt is within ±{@link #conflictWindowHours} of this race.
     * No-op for a race without a scheduled start (not published yet).
     */
    private void guardNoTimeConflict(Race race, UUID refereeUserId) {
        OffsetDateTime start = race.getScheduledStartAt();
        if (start == null) {
            return;
        }
        OffsetDateTime windowStart = start.minusHours(conflictWindowHours);
        OffsetDateTime windowEnd = start.plusHours(conflictWindowHours);
        if (refereeAssignmentRepository.existsRefereeConflictInWindow(
                refereeUserId, race.getRaceId(), windowStart, windowEnd)) {
            throw new AppException(ErrorCode.REFEREE_TIME_CONFLICT);
        }
    }

    // =========================================================================
    // DASHBOARD
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public StaffingDashboardResponse getDashboard() {
        long totalScheduledRaces = raceRepository.countByDeletedFalse();
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

        // Soft-deleted races must not appear in the staffing list — their detail page 404s (GET
        // /races/{id} uses findByRaceIdAndDeletedFalse), which reads as "can't load race".
        Page<Race> racePage = raceRepository.findByDeletedFalse(pageable);

        List<UUID> raceIds = racePage.getContent().stream()
                .map(Race::getRaceId)
                .toList();

        // A race may have a panel of several referees — group them all (no dedup).
        Map<UUID, List<RefereeAssignment>> assignmentsByRace = raceIds.stream()
                .flatMap(raceId -> refereeAssignmentRepository
                        .findByRace_RaceIdAndStatusNot(raceId, REVOKED).stream())
                .collect(Collectors.groupingBy(ra -> ra.getRace().getRaceId()));

        List<RaceAssignmentResponse> responses = racePage.getContent().stream()
                .map(race -> buildRaceAssignmentResponse(race,
                        assignmentsByRace.getOrDefault(race.getRaceId(), List.of())))
                .filter(response -> matchesFilters(response, filter))
                .toList();

        return new PageImpl<>(responses, pageable, racePage.getTotalElements());
    }

    // =========================================================================
    // GET RACE PANEL  (all referees assigned to one race)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<RefereeAssignmentResponse> getRacePanel(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new AppException(ErrorCode.RACE_NOT_FOUND);
        }
        return refereeAssignmentRepository.findByRace_RaceIdAndStatusNot(raceId, REVOKED).stream()
                .map(this::mapToAssignmentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getAssignedRaceIds(UUID refereeUserId) {
        if (refereeUserId == null) {
            return List.of();
        }
        return refereeAssignmentRepository.findRaceIdsByReferee(refereeUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefereeAssignmentResponse> getMyAssignments(UUID refereeUserId) {
        if (refereeUserId == null) {
            return List.of();
        }
        return refereeAssignmentRepository.findByReferee_UserIdAndStatusNot(refereeUserId, REVOKED).stream()
                .map(this::mapToAssignmentResponse)
                .toList();
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

        // Block if the referee is busy at another race within the ±window (FR-04).
        guardNoTimeConflict(race, request.getRefereeUserId());

        User createdBy = currentUserId != null
                ? userRepository.findByUserIdAndDeletedFalse(currentUserId).orElse(null)
                : null;

        RefereeAssignment assignment = reactivateOrInsert(race, referee, request.getPanelRole(), createdBy);

        RefereeAssignmentResponse response = mapToAssignmentResponse(assignment);
        notifyRefereeAssigned(referee, race); // FR-06 best-effort
        return response;
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

        // Block if the NEW referee is busy at another race within the ±window (FR-04).
        guardNoTimeConflict(existing.getRace(), request.getNewRefereeUserId());

        existing.setStatus(REVOKED);
        refereeAssignmentRepository.save(existing);
        notifyRefereeRemoved(existing.getReferee(), existing.getRace()); // FR-06: tell the outgoing referee

        User createdBy = currentUserId != null
                ? userRepository.findByUserIdAndDeletedFalse(currentUserId).orElse(null)
                : null;

        PanelRole panelRole = request.getPanelRole() != null
                ? request.getPanelRole()
                : existing.getPanelRole();

        // Reactivate any previously-revoked (race, newReferee) row in place instead of inserting a
        // duplicate that would collide with UNIQUE(race_id, referee_user_id) (RT-HIGH #6).
        RefereeAssignment newAssignment = reactivateOrInsert(
                existing.getRace(), newReferee, panelRole, createdBy);

        RefereeAssignmentResponse response = mapToAssignmentResponse(newAssignment);
        notifyRefereeAssigned(newReferee, existing.getRace()); // FR-06 best-effort
        return response;
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
        notifyRefereeRemoved(assignment.getReferee(), assignment.getRace()); // FR-06
    }

    /** Best-effort notify a referee they were removed/swapped out of a race panel (FR-06). */
    private void notifyRefereeRemoved(User referee, Race race) {
        if (referee == null) {
            return;
        }
        notificationService.notifyUser(referee.getUserId(), "Removed from race panel",
                "You have been removed from the officiating panel"
                        + (race != null ? " for race \"" + race.getName() + "\"." : "."));
    }

    // =========================================================================
    // REFEREE ACCEPT / DECLINE (CN1) — the referee's own actions
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<RefereeAssignmentResponse> getMyRaceAssignments(UUID refereeUserId) {
        if (refereeUserId == null) {
            return List.of();
        }
        return refereeAssignmentRepository.findByReferee_UserIdAndStatusNot(refereeUserId, REVOKED).stream()
                .map(this::mapToAssignmentResponse)
                .toList();
    }

    @Override
    @Transactional
    public RefereeAssignmentResponse acceptAssignment(UUID refereeUserId, UUID refAssignmentId) {
        RefereeAssignment assignment = loadOwnedPendingAssignment(refereeUserId, refAssignmentId);
        assignment.setStatus(CONFIRMED);
        assignment.setRespondedAt(OffsetDateTime.now());
        return mapToAssignmentResponse(refereeAssignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public RefereeAssignmentResponse declineAssignment(UUID refereeUserId, UUID refAssignmentId, String reason) {
        RefereeAssignment assignment = loadOwnedPendingAssignment(refereeUserId, refAssignmentId);
        assignment.setStatus(DECLINED);
        assignment.setRespondedAt(OffsetDateTime.now());
        assignment.setDeclineReason(reason);
        RefereeAssignmentResponse response = mapToAssignmentResponse(refereeAssignmentRepository.save(assignment));

        // FR-04: notify the admin who assigned so they can reassign the freed slot (best-effort).
        User admin = assignment.getCreatedBy();
        Race race = assignment.getRace();
        User referee = assignment.getReferee();
        if (admin != null) {
            String who = referee != null ? referee.getFullName() : "A referee";
            String where = race != null ? " for race \"" + race.getName() + "\"" : "";
            String why = (reason != null && !reason.isBlank()) ? " Reason: " + reason : "";
            notificationService.notifyUser(admin.getUserId(), "Referee declined an assignment",
                    who + " declined the officiating assignment" + where + "." + why);
        }
        return response;
    }

    /** Load an assignment that must exist, belong to the caller, and still be ASSIGNED (pending). */
    private RefereeAssignment loadOwnedPendingAssignment(UUID refereeUserId, UUID refAssignmentId) {
        RefereeAssignment assignment = refereeAssignmentRepository.findById(refAssignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.REFEREE_ASSIGNMENT_NOT_FOUND));
        if (assignment.getReferee() == null
                || !assignment.getReferee().getUserId().equals(refereeUserId)) {
            throw new AppException(ErrorCode.REFEREE_ASSIGNMENT_NOT_OWNED);
        }
        if (assignment.getStatus() != ASSIGNED) {
            throw new AppException(ErrorCode.REFEREE_ASSIGNMENT_NOT_PENDING);
        }
        return assignment;
    }

    /** Best-effort notify a referee they were assigned/reassigned to officiate a race (FR-06). */
    private void notifyRefereeAssigned(User referee, Race race) {
        if (referee == null) {
            return;
        }
        notificationService.notifyUser(referee.getUserId(), "New race assignment",
                "You have been assigned to officiate"
                        + (race != null ? " race \"" + race.getName() + "\"." : " a race.")
                        + " Please accept or decline it.");
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Assign {@code referee} to {@code race}: if a prior (race, referee) row exists (INCLUDING a
     * REVOKED one), reactivate that SAME row in place — preserving its id and audit trail — instead
     * of inserting a duplicate that would collide with UNIQUE(race_id, referee_user_id). Otherwise
     * insert a fresh row. Callers must have already rejected an active (non-REVOKED) duplicate.
     */
    private RefereeAssignment reactivateOrInsert(Race race, User referee, PanelRole panelRole, User createdBy) {
        RefereeAssignment assignment = refereeAssignmentRepository
                .findFirstByRace_RaceIdAndReferee_UserId(race.getRaceId(), referee.getUserId())
                .orElse(null);
        if (assignment != null) {
            // Defensive invariant: callers must have already rejected an ACTIVE (non-REVOKED) duplicate
            // (REFEREE_ALREADY_ASSIGNED). We only ever reactivate a REVOKED row — never silently reset a
            // live ASSIGNED/CONFIRMED/DECLINED one.
            if (assignment.getStatus() != REVOKED) {
                throw new AppException(ErrorCode.REFEREE_ALREADY_ASSIGNED);
            }
            assignment.setStatus(ASSIGNED);
            assignment.setPanelRole(panelRole);
            assignment.setRefCode(generateRefCode());
            assignment.setAssignedAt(OffsetDateTime.now());
            assignment.setRespondedAt(null);
            assignment.setDeclineReason(null);
            assignment.setCreatedBy(createdBy);
        } else {
            assignment = RefereeAssignment.builder()
                    .race(race)
                    .referee(referee)
                    .panelRole(panelRole)
                    .refCode(generateRefCode())
                    .status(ASSIGNED)
                    .assignedAt(OffsetDateTime.now())
                    .createdBy(createdBy)
                    .build();
        }
        return refereeAssignmentRepository.save(assignment);
    }

    /** Generate a unique, human-readable per-race referee code, e.g. {@code REF-3F9A2C}. */
    private String generateRefCode() {
        String code;
        do {
            code = "REF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } while (refereeAssignmentRepository.existsByRefCode(code));
        return code;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> conflictingRefereeIds(UUID raceId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));
        OffsetDateTime start = race.getScheduledStartAt();
        if (start == null) {
            return List.of();
        }
        return refereeAssignmentRepository.findRefereeIdsWithConflictInWindow(
                raceId, start.minusHours(conflictWindowHours), start.plusHours(conflictWindowHours));
    }

    private User loadReferee(UUID userId) {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        Role role = user.getRole();
        if (role == null || !REFEREE_ROLE_CODE.equals(role.getRoleCode())) {
            throw new AppException(ErrorCode.STAFF_NOT_REFEREE);
        }
        // Risk (a): a referee with an unverified email can't receive the post-race OTP → block at
        // assignment time (locked decision 2026-07-06) so the failure surfaces early, not post-race.
        if (!user.isEmailVerified()) {
            throw new AppException(ErrorCode.REFEREE_EMAIL_UNVERIFIED);
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
                .panelRole(ra.getPanelRole() != null ? ra.getPanelRole().name() : null)
                .refCode(ra.getRefCode())
                .status(ra.getStatus() != null ? ra.getStatus().name() : null)
                .assignedAt(ra.getAssignedAt())
                .respondedAt(ra.getRespondedAt())
                .declineReason(ra.getDeclineReason())
                .createdAt(ra.getCreatedAt())
                .createdByUserId(createdBy != null ? createdBy.getUserId() : null)
                .createdByName(createdBy != null ? createdBy.getFullName() : null)
                .build();
    }

    private RaceAssignmentResponse buildRaceAssignmentResponse(Race race, List<RefereeAssignment> assignments) {
        RaceAssignmentResponse.RaceAssignmentResponseBuilder builder = RaceAssignmentResponse.builder()
                .raceId(race.getRaceId())
                .raceName(race.getName())
                .raceCode(race.getRaceCode())
                .raceStatus(race.getStatus() != null ? race.getStatus().name() : null)
                .scheduledStartAt(race.getScheduledStartAt())
                .assignmentCount(assignments.size());

        if (!assignments.isEmpty()) {
            RefereeAssignment assignment = assignments.get(0); // primary (for the table row preview)
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
