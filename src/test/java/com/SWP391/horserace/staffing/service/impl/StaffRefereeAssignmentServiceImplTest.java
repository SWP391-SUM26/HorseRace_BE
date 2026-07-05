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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffRefereeAssignmentServiceImplTest {

    @Mock RefereeAssignmentRepository refereeAssignmentRepository;
    @Mock RaceRepository raceRepository;
    @Mock UserRepository userRepository;

    @InjectMocks StaffRefereeAssignmentServiceImpl service;

    private User referee(UUID id) {
        return User.builder().userId(id).fullName("Ref")
                .role(Role.builder().roleCode("RACE_REFEREE").build()).build();
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
}
