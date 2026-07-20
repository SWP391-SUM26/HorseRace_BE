package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.assignments.entity.PanelRole;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.dto.AssignRefereeRequest;
import com.SWP391.horserace.staffing.dto.RefereeAssignmentResponse;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffRefereeAssignmentServiceImplTest {

    @Mock RefereeAssignmentRepository refereeAssignmentRepository;
    @Mock RaceRepository raceRepository;
    @Mock UserRepository userRepository;
    @Mock com.SWP391.horserace.notifications.service.NotificationService notificationService;

    @InjectMocks StaffRefereeAssignmentServiceImpl service;

    private User referee(UUID id) {
        return User.builder().userId(id).fullName("Ref").emailVerified(true)
                .role(Role.builder().roleCode("RACE_REFEREE").build()).build();
    }

    private com.SWP391.horserace.assignments.entity.RefereeAssignment assignment(
            UUID id, UUID refereeId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus status) {
        return com.SWP391.horserace.assignments.entity.RefereeAssignment.builder()
                .refAssignmentId(id)
                .referee(User.builder().userId(refereeId).fullName("Ref").build())
                .race(Race.builder().raceId(UUID.randomUUID()).name("R1").build())
                .status(status)
                .build();
    }

    private AssignRefereeRequest req(UUID raceId, UUID refId) {
        return AssignRefereeRequest.builder().raceId(raceId).refereeUserId(refId)
                .panelRole(PanelRole.JUDGE).build();
    }

    @Test
    void assignReferee_refereeBusyInWindow_throwsTimeConflict() {
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).scheduledStartAt(OffsetDateTime.now()).build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(referee(refId)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(eq(raceId), eq(refId), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.existsRefereeConflictInWindow(eq(refId), eq(raceId), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.assignReferee(req(raceId, refId), null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_TIME_CONFLICT);
        verify(refereeAssignmentRepository, never()).save(any());
    }

    @Test
    void assignReferee_activeDuplicate_stillThrowsAlreadyAssigned() {
        // Regression: the reassign/reactivate refactor must NOT weaken the active-duplicate guard.
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(referee(refId)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(eq(raceId), eq(refId), any()))
                .thenReturn(true); // an active (non-REVOKED) assignment already exists

        assertThatThrownBy(() -> service.assignReferee(req(raceId, refId), null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_ALREADY_ASSIGNED);
        verify(refereeAssignmentRepository, never()).save(any());
    }

    @Test
    void assignReferee_noTimeConflict_assigns() {
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).scheduledStartAt(OffsetDateTime.now()).build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(referee(refId)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(eq(raceId), eq(refId), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.existsRefereeConflictInWindow(eq(refId), eq(raceId), any(), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefereeAssignmentResponse res = service.assignReferee(req(raceId, refId), null);

        assertThat(res.getStatus()).isEqualTo("ASSIGNED");
        verify(refereeAssignmentRepository).save(any());
    }

    @Test
    void assignReferee_raceWithoutScheduledStart_skipsConflictCheck() {
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).build(); // no scheduledStartAt
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(referee(refId)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(eq(raceId), eq(refId), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.assignReferee(req(raceId, refId), null);

        verify(refereeAssignmentRepository, never()).existsRefereeConflictInWindow(any(), any(), any(), any());
        verify(refereeAssignmentRepository).save(any());
    }

    // ── CN1: email-verified precondition + referee notification (FR-06, Risk a) ──

    @Test
    void assignReferee_unverifiedEmail_throwsEmailUnverified() {
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).scheduledStartAt(OffsetDateTime.now()).build();
        User unverified = User.builder().userId(refId).fullName("Ref").emailVerified(false)
                .role(Role.builder().roleCode("RACE_REFEREE").build()).build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> service.assignReferee(req(raceId, refId), null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_EMAIL_UNVERIFIED);
        verify(refereeAssignmentRepository, never()).save(any());
    }

    @Test
    void assignReferee_verifiedEmail_notifiesReferee() {
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).name("Gold Cup").build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(referee(refId)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(eq(raceId), eq(refId), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.assignReferee(req(raceId, refId), null);

        verify(notificationService).notifyUser(eq(refId), any(), any());
    }

    // ── #6: reactivate a previously-revoked (race, referee) row instead of a duplicate INSERT ──

    @Test
    void assignReferee_previouslyRevoked_reactivatesRowNoInsert() {
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID(), assignId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).name("R1").build(); // no scheduledStartAt -> no conflict check
        var revoked = com.SWP391.horserace.assignments.entity.RefereeAssignment.builder()
                .refAssignmentId(assignId)
                .race(race)
                .referee(User.builder().userId(refId).build())
                .status(com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.REVOKED)
                .build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(referee(refId)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(eq(raceId), eq(refId), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.findFirstByRace_RaceIdAndReferee_UserId(raceId, refId))
                .thenReturn(Optional.of(revoked));
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefereeAssignmentResponse res = service.assignReferee(req(raceId, refId), null);

        assertThat(res.getStatus()).isEqualTo("ASSIGNED");
        // The SAME row was flipped back to ASSIGNED — no fresh INSERT (id preserved).
        assertThat(revoked.getStatus())
                .isEqualTo(com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.ASSIGNED);
        ArgumentCaptor<com.SWP391.horserace.assignments.entity.RefereeAssignment> cap =
                ArgumentCaptor.forClass(com.SWP391.horserace.assignments.entity.RefereeAssignment.class);
        verify(refereeAssignmentRepository).save(cap.capture());
        assertThat(cap.getValue().getRefAssignmentId()).isEqualTo(assignId);
    }

    @Test
    void assignReferee_noExistingRow_insertsNew() {
        UUID raceId = UUID.randomUUID(), refId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).name("R1").build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(refId)).thenReturn(Optional.of(referee(refId)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(eq(raceId), eq(refId), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.findFirstByRace_RaceIdAndReferee_UserId(raceId, refId))
                .thenReturn(Optional.empty());
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefereeAssignmentResponse res = service.assignReferee(req(raceId, refId), null);

        assertThat(res.getStatus()).isEqualTo("ASSIGNED");
        ArgumentCaptor<com.SWP391.horserace.assignments.entity.RefereeAssignment> cap =
                ArgumentCaptor.forClass(com.SWP391.horserace.assignments.entity.RefereeAssignment.class);
        verify(refereeAssignmentRepository).save(cap.capture());
        assertThat(cap.getValue().getRefAssignmentId()).isNull(); // brand-new row
    }

    @Test
    void reassignReferee_revokedThenReassign_succeeds() {
        UUID id = UUID.randomUUID(), oldRef = UUID.randomUUID(), newRef = UUID.randomUUID();
        var existing = assignment(id, oldRef, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.CONFIRMED);
        when(refereeAssignmentRepository.findByRefAssignmentIdAndStatusNot(eq(id), any()))
                .thenReturn(Optional.of(existing));
        when(userRepository.findByUserIdAndDeletedFalse(newRef)).thenReturn(Optional.of(referee(newRef)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(any(), eq(newRef), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.findFirstByRace_RaceIdAndReferee_UserId(any(), eq(newRef)))
                .thenReturn(Optional.empty());
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = com.SWP391.horserace.staffing.dto.ReassignRefereeRequest.builder()
                .newRefereeUserId(newRef).build();

        RefereeAssignmentResponse res = service.reassignReferee(id, request, null);

        assertThat(res.getStatus()).isEqualTo("ASSIGNED");
        assertThat(existing.getStatus())
                .isEqualTo(com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.REVOKED);
    }

    @Test
    void reassignReferee_newRefereePreviouslyRevoked_reactivatesNoInsert() {
        UUID id = UUID.randomUUID(), oldRef = UUID.randomUUID(), newRef = UUID.randomUUID(), revokedId = UUID.randomUUID();
        var existing = assignment(id, oldRef, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.CONFIRMED);
        UUID raceId = existing.getRace().getRaceId();
        var revokedNew = com.SWP391.horserace.assignments.entity.RefereeAssignment.builder()
                .refAssignmentId(revokedId)
                .race(existing.getRace())
                .referee(User.builder().userId(newRef).build())
                .status(com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.REVOKED)
                .build();
        when(refereeAssignmentRepository.findByRefAssignmentIdAndStatusNot(eq(id), any()))
                .thenReturn(Optional.of(existing));
        when(userRepository.findByUserIdAndDeletedFalse(newRef)).thenReturn(Optional.of(referee(newRef)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(any(), eq(newRef), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.findFirstByRace_RaceIdAndReferee_UserId(raceId, newRef))
                .thenReturn(Optional.of(revokedNew));
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = com.SWP391.horserace.staffing.dto.ReassignRefereeRequest.builder()
                .newRefereeUserId(newRef).build();

        RefereeAssignmentResponse res = service.reassignReferee(id, request, null);

        assertThat(res.getStatus()).isEqualTo("ASSIGNED");
        // The incoming referee's previously-revoked row was reactivated in place (no fresh INSERT).
        assertThat(revokedNew.getStatus())
                .isEqualTo(com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.ASSIGNED);
        ArgumentCaptor<com.SWP391.horserace.assignments.entity.RefereeAssignment> cap =
                ArgumentCaptor.forClass(com.SWP391.horserace.assignments.entity.RefereeAssignment.class);
        verify(refereeAssignmentRepository, times(2)).save(cap.capture()); // 1) revoke existing 2) reactivate
        assertThat(cap.getAllValues().get(1).getRefAssignmentId()).isEqualTo(revokedId);
    }

    @Test
    void reassignReferee_notifiesOutgoingAndIncomingReferee() {
        UUID id = UUID.randomUUID(), oldRef = UUID.randomUUID(), newRef = UUID.randomUUID();
        var existing = assignment(id, oldRef, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.CONFIRMED);
        when(refereeAssignmentRepository.findByRefAssignmentIdAndStatusNot(eq(id), any()))
                .thenReturn(Optional.of(existing));
        when(userRepository.findByUserIdAndDeletedFalse(newRef)).thenReturn(Optional.of(referee(newRef)));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusNot(any(), eq(newRef), any()))
                .thenReturn(false);
        when(refereeAssignmentRepository.existsByRefCode(any())).thenReturn(false);
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = com.SWP391.horserace.staffing.dto.ReassignRefereeRequest.builder()
                .newRefereeUserId(newRef).build();

        service.reassignReferee(id, request, null);

        verify(notificationService).notifyUser(eq(oldRef), any(), any()); // outgoing
        verify(notificationService).notifyUser(eq(newRef), any(), any()); // incoming
    }

    @Test
    void removeAssignment_notifiesRevokedReferee() {
        UUID id = UUID.randomUUID(), refId = UUID.randomUUID();
        var a = assignment(id, refId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.ASSIGNED);
        when(refereeAssignmentRepository.findById(id)).thenReturn(Optional.of(a));
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.removeAssignment(id);

        verify(notificationService).notifyUser(eq(refId), any(), any());
    }

    // ── CN1: referee accept/decline (FR-01..FR-04) ──

    @Test
    void acceptAssignment_ownAssigned_transitionsToConfirmed() {
        UUID id = UUID.randomUUID(), refId = UUID.randomUUID();
        var a = assignment(id, refId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.ASSIGNED);
        when(refereeAssignmentRepository.findById(id)).thenReturn(Optional.of(a));
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefereeAssignmentResponse res = service.acceptAssignment(refId, id);

        assertThat(res.getStatus()).isEqualTo("CONFIRMED");
        assertThat(a.getRespondedAt()).isNotNull();
    }

    @Test
    void acceptAssignment_notOwner_throwsNotOwned() {
        UUID id = UUID.randomUUID(), refId = UUID.randomUUID(), otherRef = UUID.randomUUID();
        var a = assignment(id, refId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.ASSIGNED);
        when(refereeAssignmentRepository.findById(id)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.acceptAssignment(otherRef, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_ASSIGNMENT_NOT_OWNED);
        verify(refereeAssignmentRepository, never()).save(any());
    }

    @Test
    void acceptAssignment_alreadyConfirmed_throwsNotPending() {
        UUID id = UUID.randomUUID(), refId = UUID.randomUUID();
        var a = assignment(id, refId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.CONFIRMED);
        when(refereeAssignmentRepository.findById(id)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.acceptAssignment(refId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_ASSIGNMENT_NOT_PENDING);
    }

    @Test
    void declineAssignment_ownAssigned_transitionsToDeclined_notifiesAdmin() {
        UUID id = UUID.randomUUID(), refId = UUID.randomUUID(), adminId = UUID.randomUUID();
        var a = assignment(id, refId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.ASSIGNED);
        a.setCreatedBy(User.builder().userId(adminId).build());
        when(refereeAssignmentRepository.findById(id)).thenReturn(Optional.of(a));
        when(refereeAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefereeAssignmentResponse res = service.declineAssignment(refId, id, "Unavailable");

        assertThat(res.getStatus()).isEqualTo("DECLINED");
        assertThat(a.getDeclineReason()).isEqualTo("Unavailable");
        assertThat(a.getRespondedAt()).isNotNull();
        verify(notificationService).notifyUser(eq(adminId), any(), any());
    }

    @Test
    void declineAssignment_notPending_throws() {
        UUID id = UUID.randomUUID(), refId = UUID.randomUUID();
        var a = assignment(id, refId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.DECLINED);
        when(refereeAssignmentRepository.findById(id)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.declineAssignment(refId, id, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_ASSIGNMENT_NOT_PENDING);
        verify(refereeAssignmentRepository, never()).save(any());
    }

    @Test
    void getMyRaceAssignments_queriesByRefereeId() {
        UUID refId = UUID.randomUUID();
        when(refereeAssignmentRepository.findByReferee_UserIdAndStatusNot(eq(refId), any()))
                .thenReturn(java.util.List.of());

        service.getMyRaceAssignments(refId);

        verify(refereeAssignmentRepository).findByReferee_UserIdAndStatusNot(eq(refId), any());
    }
}
