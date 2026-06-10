package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.dto.CreateStaffRequest;
import com.SWP391.horserace.staffing.dto.StaffFilterRequest;
import com.SWP391.horserace.staffing.dto.StaffResponse;
import com.SWP391.horserace.staffing.dto.UpdateStaffRequest;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.staffing.service.StaffService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import com.SWP391.horserace.users.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private static final String REFEREE_ROLE_CODE = "RACE_REFEREE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefereeAssignmentRepository refereeAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    // =========================================================================
    // GET STAFF LIST  (tasks 136 + 138 + 140 + 142)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<StaffResponse> getStaffList(StaffFilterRequest filter) {
        Specification<User> spec = buildSpecification(filter);

        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(filter.getSortDir())
                        ? Sort.Direction.DESC : Sort.Direction.ASC,
                filter.getSortBy()
        );
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<User> userPage = userRepository.findAll(spec, pageable);

        // Pre-load assignment counts for all referees in one query.
        Map<UUID, Long> assignmentCounts = refereeAssignmentRepository
                .countActiveAssignmentsPerReferee()
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));

        return userPage.map(user -> mapToStaffResponse(user, assignmentCounts));
    }

    // =========================================================================
    // CREATE STAFF  (task 144)
    // =========================================================================
    @Override
    @Transactional
    public StaffResponse createStaff(CreateStaffRequest request) {
        // Validate email uniqueness.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.STAFF_EMAIL_EXISTED);
        }

        // Resolve the RACE_REFEREE role.
        Role refereeRole = roleRepository.findByRoleCode(REFEREE_ROLE_CODE)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        // Auto-generate user code.
        String userCode = generateUserCode();

        User user = User.builder()
                .role(refereeRole)
                .userCode(userCode)
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        return mapToStaffResponse(saved, Map.of());
    }

    // =========================================================================
    // UPDATE STAFF  (task 146)
    // =========================================================================
    @Override
    @Transactional
    public StaffResponse updateStaff(UUID userId, UpdateStaffRequest request) {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        // Verify user is a referee.
        if (user.getRole() == null || !REFEREE_ROLE_CODE.equals(user.getRole().getRoleCode())) {
            throw new AppException(ErrorCode.STAFF_NOT_REFEREE);
        }

        // Partial update: only apply non-null fields.
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (request.getStatus() != null) {
            user.setStatus(UserStatus.valueOf(request.getStatus().toUpperCase()));
        }

        User saved = userRepository.save(user);

        Map<UUID, Long> assignmentCounts = refereeAssignmentRepository
                .countActiveAssignmentsPerReferee()
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));

        return mapToStaffResponse(saved, assignmentCounts);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Builds a JPA Specification that:
     * 1. Filters only users with the RACE_REFEREE role.
     * 2. Excludes soft-deleted users.
     * 3. Optionally applies a search term (fullName / email / userCode LIKE).
     * 4. Optionally filters by user status.
     */
    private Specification<User> buildSpecification(StaffFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only referees.
            Join<User, Role> roleJoin = root.join("role");
            predicates.add(cb.equal(roleJoin.get("roleCode"), REFEREE_ROLE_CODE));

            // Not deleted.
            predicates.add(cb.isFalse(root.get("deleted")));

            // Search (case-insensitive).
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().trim().toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("userCode")), pattern)
                );
                predicates.add(searchPredicate);
            }

            // Status filter.
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"),
                        UserStatus.valueOf(filter.getStatus().toUpperCase())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private StaffResponse mapToStaffResponse(User user, Map<UUID, Long> assignmentCounts) {
        Role role = user.getRole();
        return StaffResponse.builder()
                .userId(user.getUserId())
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .kycStatus(user.getKycStatus() != null ? user.getKycStatus().name() : null)
                .roleCode(role != null ? role.getRoleCode() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .createdAt(user.getCreatedAt())
                .assignedRaceCount(assignmentCounts.getOrDefault(user.getUserId(), 0L))
                .build();
    }

    /**
     * Auto-generates a sequential user code like USR0009, USR0010...
     * Uses the current max user_code number + 1.
     */
    private String generateUserCode() {
        long count = userRepository.count();
        return String.format("USR%04d", count + 1);
    }
}
