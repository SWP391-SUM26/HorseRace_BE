package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.assignments.entity.TournamentRefereeAssignment;
import com.SWP391.horserace.assignments.entity.TournamentRefereeStatus;
import com.SWP391.horserace.notifications.service.NotificationService;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.dto.InviteTournamentRefereeRequest;
import com.SWP391.horserace.staffing.dto.TournamentRefereeAssignmentResponse;
import com.SWP391.horserace.staffing.repository.TournamentRefereeAssignmentRepository;
import com.SWP391.horserace.staffing.service.TournamentRefereeService;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentRefereeServiceImpl implements TournamentRefereeService {

    private static final String REFEREE_ROLE_CODE = "RACE_REFEREE";
    private static final TournamentRefereeStatus REVOKED = TournamentRefereeStatus.REVOKED;
    private static final TournamentRefereeStatus INVITED = TournamentRefereeStatus.INVITED;

    private final TournamentRefereeAssignmentRepository repository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ── admin ──

    @Override
    @Transactional
    public TournamentRefereeAssignmentResponse invite(UUID currentUserId, InviteTournamentRefereeRequest request) {
        Tournament tournament = tournamentRepository.findById(request.getTournamentId())
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        User referee = loadReferee(request.getRefereeUserId());

        if (repository.existsByTournament_TournamentIdAndReferee_UserIdAndStatusNot(
                request.getTournamentId(), request.getRefereeUserId(), REVOKED)) {
            throw new AppException(ErrorCode.TOURNAMENT_REFEREE_ALREADY_INVITED);
        }

        User invitedBy = currentUserId != null
                ? userRepository.findByUserIdAndDeletedFalse(currentUserId).orElse(null)
                : null;

        TournamentRefereeAssignment invitation = TournamentRefereeAssignment.builder()
                .tournament(tournament)
                .referee(referee)
                .panelRole(request.getPanelRole())
                .status(INVITED)
                .invitedBy(invitedBy)
                .invitedAt(OffsetDateTime.now())
                .build();

        TournamentRefereeAssignment saved = repository.save(invitation);

        String role = request.getPanelRole() != null ? request.getPanelRole().name().toLowerCase() : "referee";
        notificationService.notifyUser(referee.getUserId(),
                "Tournament invitation",
                "You've been invited to officiate "
                        + (tournament.getName() != null ? tournament.getName() : "a tournament")
                        + " as " + role + ".");

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentRefereeAssignmentResponse> listByTournament(UUID tournamentId, String status) {
        TournamentRefereeStatus filter = parseStatus(status);
        List<TournamentRefereeAssignment> rows = filter != null
                ? repository.findByTournament_TournamentIdAndStatusOrderByInvitedAtDesc(tournamentId, filter)
                : repository.findByTournament_TournamentIdOrderByInvitedAtDesc(tournamentId);
        return rows.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public void revoke(UUID id) {
        TournamentRefereeAssignment invitation = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_INVITATION_NOT_FOUND));
        invitation.setStatus(REVOKED);
        invitation.setRespondedAt(OffsetDateTime.now());
        repository.save(invitation);
    }

    // ── referee ──

    @Override
    @Transactional(readOnly = true)
    public List<TournamentRefereeAssignmentResponse> listMyInvitations(UUID refereeUserId) {
        if (refereeUserId == null) {
            return List.of();
        }
        return repository.findByReferee_UserIdAndStatusOrderByInvitedAtDesc(refereeUserId, INVITED)
                .stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public TournamentRefereeAssignmentResponse accept(UUID id, UUID refereeUserId) {
        return respond(id, refereeUserId, TournamentRefereeStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public TournamentRefereeAssignmentResponse reject(UUID id, UUID refereeUserId) {
        return respond(id, refereeUserId, TournamentRefereeStatus.DECLINED);
    }

    // ── helpers ──

    private TournamentRefereeAssignmentResponse respond(UUID id, UUID refereeUserId, TournamentRefereeStatus to) {
        if (refereeUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        TournamentRefereeAssignment invitation = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_INVITATION_NOT_FOUND));

        if (invitation.getReferee() == null || !refereeUserId.equals(invitation.getReferee().getUserId())) {
            throw new AppException(ErrorCode.NOT_INVITED_REFEREE);
        }
        if (invitation.getStatus() != INVITED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVITATION_NOT_PENDING);
        }

        invitation.setStatus(to);
        invitation.setRespondedAt(OffsetDateTime.now());
        TournamentRefereeAssignment saved = repository.save(invitation);

        // Tell the admin who invited them how the referee responded.
        if (invitation.getInvitedBy() != null) {
            String tName = invitation.getTournament() != null && invitation.getTournament().getName() != null
                    ? invitation.getTournament().getName() : "a tournament";
            String who = invitation.getReferee().getFullName() != null
                    ? invitation.getReferee().getFullName() : "A referee";
            notificationService.notifyUser(invitation.getInvitedBy().getUserId(),
                    "Tournament invitation " + (to == TournamentRefereeStatus.ACCEPTED ? "accepted" : "declined"),
                    who + " " + (to == TournamentRefereeStatus.ACCEPTED ? "accepted" : "declined")
                            + " the invitation to officiate " + tName + ".");
        }
        return mapToResponse(saved);
    }

    private User loadReferee(UUID userId) {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        Role role = user.getRole();
        if (role == null || !REFEREE_ROLE_CODE.equals(role.getRoleCode())) {
            throw new AppException(ErrorCode.STAFF_NOT_REFEREE);
        }
        return user;
    }

    private TournamentRefereeStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return TournamentRefereeStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TournamentRefereeAssignmentResponse mapToResponse(TournamentRefereeAssignment a) {
        Tournament t = a.getTournament();
        User ref = a.getReferee();
        return TournamentRefereeAssignmentResponse.builder()
                .id(a.getId())
                .tournamentId(t != null ? t.getTournamentId() : null)
                .tournamentName(t != null ? t.getName() : null)
                .refereeUserId(ref != null ? ref.getUserId() : null)
                .refereeName(ref != null ? ref.getFullName() : null)
                .refereeAvatarUrl(ref != null ? ref.getAvatarUrl() : null)
                .panelRole(a.getPanelRole() != null ? a.getPanelRole().name() : null)
                .status(a.getStatus() != null ? a.getStatus().name() : null)
                .invitedAt(a.getInvitedAt())
                .respondedAt(a.getRespondedAt())
                .build();
    }
}
