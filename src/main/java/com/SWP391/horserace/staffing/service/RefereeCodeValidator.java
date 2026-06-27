package com.SWP391.horserace.staffing.service;

import com.SWP391.horserace.assignments.entity.RefereeAssignment;
import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Gates report submissions (results, violations) behind the per-race code the admin issued to the
 * officiating referee. An ADMIN may submit without a code; an assigned referee must quote the exact
 * code on their assignment so the admin can audit who filed each report.
 */
@Service
@RequiredArgsConstructor
public class RefereeCodeValidator {

    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final RefereeAssignmentRepository refereeAssignmentRepository;
    private final UserRepository userRepository;

    /**
     * @param userId       the signed-in user submitting the report
     * @param raceId       the race the report is filed against
     * @param providedCode the code the referee typed (ignored for admins)
     */
    public void validate(UUID userId, UUID raceId, String providedCode) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Admins can record/correct results without a per-race code.
        if (user.getRole() != null && ADMIN_ROLE_CODE.equals(user.getRole().getRoleCode())) {
            return;
        }

        RefereeAssignment assignment = refereeAssignmentRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndStatusNot(
                        raceId, userId, RefereeAssignmentStatus.REVOKED)
                .orElseThrow(() -> new AppException(ErrorCode.REFEREE_NOT_ASSIGNED));

        if (providedCode == null || providedCode.isBlank()) {
            throw new AppException(ErrorCode.REFEREE_CODE_REQUIRED);
        }
        if (assignment.getRefCode() == null
                || !assignment.getRefCode().equalsIgnoreCase(providedCode.trim())) {
            throw new AppException(ErrorCode.REFEREE_CODE_INVALID);
        }
    }
}
