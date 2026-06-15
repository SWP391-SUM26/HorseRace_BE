package com.SWP391.horserace.assignments.service.impl;

import com.SWP391.horserace.assignments.dto.InvitationFilterRequest;
import com.SWP391.horserace.assignments.dto.InvitationResponse;
import com.SWP391.horserace.assignments.dto.SendInvitationRequest;
import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.assignments.service.JockeyAssignmentService;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JockeyAssignmentServiceImpl implements JockeyAssignmentService {

    private final JockeyAssignmentRepository assignmentRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // SEND INVITATION  (Horse Owner → Jockey)
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public InvitationResponse sendInvitation(SendInvitationRequest request, UUID currentUserId) {
        // 1. Load entry with full chain
        RaceEntry entry = raceEntryRepository.findByIdWithDetails(request.getEntryId())
                .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));

        // 2. TẠM TẮT CHECK QUYỀN ĐỂ DEV/TEST (Yêu cầu từ user)
        /*
        UUID ownerUserId = entry.getRegistration().getOwner().getUserId();
        if (!ownerUserId.equals(currentUserId)) {
            throw new AppException(ErrorCode.OWNER_NOT_MATCH);
        }
        */

        // 3. TẠM TẮT CHECK TRÙNG LẶP ĐỂ DEV/TEST KHÔNG BỊ 409
        /*
        if (assignmentRepository.existsActiveByEntryId(request.getEntryId())) {
            throw new AppException(ErrorCode.ENTRY_ALREADY_ASSIGNED);
        }
        */

        // 4. Verify the jockey exists and is active
        JockeyProfile jockeyProfile = jockeyProfileRepository.findByIdAndUserActive(request.getJockeyUserId())
                .orElseThrow(() -> new AppException(ErrorCode.JOCKEY_NOT_FOUND));

        // 5. Lấy thẳng chủ ngựa làm người gửi thay vì bắt buộc truyền currentUserId hợp lệ
        User assignedByUser = entry.getRegistration().getOwner();

        // 6. Create the assignment
        JockeyAssignment assignment = JockeyAssignment.builder()
                .entry(entry)
                .jockey(jockeyProfile.getJockeyUser())
                .status(JockeyAssignmentStatus.INVITED)
                .invitedAt(OffsetDateTime.now())
                .assignedBy(assignedByUser)
                .build();

        assignment = assignmentRepository.save(assignment);

        return mapToResponse(assignment);
    }

    // -------------------------------------------------------------------------
    // GET INVITATION LIST  (filtered + paginated)
    // -------------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<InvitationResponse> getInvitations(InvitationFilterRequest filter) {
        Sort sort = buildSort(filter.getSortBy(), filter.getSortDir());
        Pageable pageable = PageRequest.of(
                filter.getPage() != null ? filter.getPage() : 0,
                filter.getSize() != null ? filter.getSize() : 10,
                sort);

        Page<JockeyAssignment> page;

        boolean hasJockey = filter.getJockeyUserId() != null;
        boolean hasOwner = filter.getOwnerUserId() != null;
        boolean hasStatus = filter.getStatus() != null && !filter.getStatus().isBlank();

        JockeyAssignmentStatus statusFilter = hasStatus ? parseStatus(filter.getStatus()) : null;

        // An unrecognized status string matches no rows — return an empty page (HTTP 200),
        // preserving the previous string-literal behavior.
        if (hasStatus && statusFilter == null) {
            return Page.empty(pageable);
        }

        if (hasJockey && hasStatus) {
            page = assignmentRepository.findByJockeyUserIdAndStatus(
                    filter.getJockeyUserId(), statusFilter, pageable);
        } else if (hasJockey) {
            page = assignmentRepository.findByJockeyUserId(filter.getJockeyUserId(), pageable);
        } else if (hasOwner && hasStatus) {
            page = assignmentRepository.findByOwnerUserIdAndStatus(
                    filter.getOwnerUserId(), statusFilter, pageable);
        } else if (hasOwner) {
            page = assignmentRepository.findByOwnerUserId(filter.getOwnerUserId(), pageable);
        } else if (hasStatus) {
            page = assignmentRepository.findByStatus(statusFilter, pageable);
        } else {
            page = assignmentRepository.findAllWithDetails(pageable);
        }

        return page.map(this::mapToResponse);
    }

    // -------------------------------------------------------------------------
    // ACCEPT INVITATION  (Jockey)
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public InvitationResponse acceptInvitation(UUID assignmentId, UUID currentUserId) {
        JockeyAssignment assignment = assignmentRepository.findByIdWithDetails(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // TẠM TẮT CHECK QUYỀN ĐỂ DEV/TEST
        /*
        // Only the invited jockey can accept
        if (!assignment.getJockey().getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_INVITED_JOCKEY);
        }
        */

        // Must be in INVITED status
        if (assignment.getStatus() != JockeyAssignmentStatus.INVITED) {
            throw new AppException(ErrorCode.INVITATION_NOT_PENDING);
        }

        assignment.setStatus(JockeyAssignmentStatus.ACCEPTED);
        assignment.setRespondedAt(OffsetDateTime.now());
        assignment = assignmentRepository.save(assignment);

        return mapToResponse(assignment);
    }

    // -------------------------------------------------------------------------
    // REJECT INVITATION  (Jockey)
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public InvitationResponse rejectInvitation(UUID assignmentId, UUID currentUserId) {
        JockeyAssignment assignment = assignmentRepository.findByIdWithDetails(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // TẠM TẮT CHECK QUYỀN ĐỂ DEV/TEST
        /*
        // Only the invited jockey can reject
        if (!assignment.getJockey().getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_INVITED_JOCKEY);
        }
        */

        // Must be in INVITED status
        if (assignment.getStatus() != JockeyAssignmentStatus.INVITED) {
            throw new AppException(ErrorCode.INVITATION_NOT_PENDING);
        }

        assignment.setStatus(JockeyAssignmentStatus.DECLINED);
        assignment.setRespondedAt(OffsetDateTime.now());
        assignment = assignmentRepository.save(assignment);

        return mapToResponse(assignment);
    }

    // -------------------------------------------------------------------------
    // CANCEL INVITATION  (Horse Owner — soft-delete)
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public void cancelInvitation(UUID assignmentId, UUID currentUserId) {
        JockeyAssignment assignment = assignmentRepository.findByIdWithDetails(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // TẠM TẮT CHECK QUYỀN ĐỂ DEV/TEST
        /*
        // Only the owner who sent the invitation can cancel
        UUID ownerUserId = assignment.getEntry().getRegistration().getOwner().getUserId();
        if (!ownerUserId.equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_INVITATION_OWNER);
        }
        */

        // Can only cancel INVITED status
        if (assignment.getStatus() != JockeyAssignmentStatus.INVITED) {
            throw new AppException(ErrorCode.INVITATION_CANNOT_CANCEL);
        }

        assignment.setStatus(JockeyAssignmentStatus.CANCELLED);
        assignment.setRespondedAt(OffsetDateTime.now());
        assignmentRepository.save(assignment);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Maps a fully-loaded JockeyAssignment entity to the rich InvitationResponse DTO.
     * Expects all associations (entry, registration, horse, race, tournament, jockey, owner)
     * to be eagerly fetched.
     */
    private InvitationResponse mapToResponse(JockeyAssignment assignment) {
        RaceEntry entry = assignment.getEntry();
        TournamentRegistration registration = entry.getRegistration();
        Horse horse = registration.getHorse();
        Race race = entry.getRace();
        Tournament tournament = race.getTournament();
        User jockey = assignment.getJockey();
        User owner = registration.getOwner();

        return InvitationResponse.builder()
                // assignment
                .assignmentId(assignment.getAssignmentId())
                .status(assignment.getStatus())
                .invitedAt(assignment.getInvitedAt())
                .respondedAt(assignment.getRespondedAt())
                .createdAt(assignment.getCreatedAt())
                // horse
                .horseId(horse.getHorseId())
                .horseName(horse.getName())
                .horseCode(horse.getHorseCode())
                // race
                .raceId(race.getRaceId())
                .raceName(race.getName())
                .raceCode(race.getRaceCode())
                .scheduledStartAt(race.getScheduledStartAt())
                .trackCondition(race.getTrackCondition())
                .distanceMeter(race.getDistanceMeter())
                // tournament
                .tournamentId(tournament.getTournamentId())
                .tournamentName(tournament.getName())
                .tournamentLocation(tournament.getLocation())
                // jockey
                .jockeyUserId(jockey.getUserId())
                .jockeyName(jockey.getFullName())
                .jockeyAvatarUrl(jockey.getAvatarUrl())
                // owner
                .ownerUserId(owner.getUserId())
                .ownerName(owner.getFullName())
                // entry
                .entryId(entry.getEntryId())
                .entryCode(entry.getEntryCode())
                .entryNo(entry.getEntryNo())
                .build();
    }

    /**
     * Parse a user-supplied status string (case-insensitive) into a {@link JockeyAssignmentStatus}.
     * Returns {@code null} when the value does not match any enum constant.
     */
    private JockeyAssignmentStatus parseStatus(String raw) {
        try {
            return JockeyAssignmentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Build a Spring Sort from user-supplied field name and direction. */
    private Sort buildSort(String sortBy, String sortDir) {
        String field = switch (sortBy != null ? sortBy : "invitedAt") {
            case "respondedAt" -> "respondedAt";
            case "createdAt" -> "createdAt";
            default -> "invitedAt";
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, field);
    }
}
