package com.SWP391.horserace.users.repository;

import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.users.dto.UserFilterRequest;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the dynamic WHERE clause for the admin user listing from {@link UserFilterRequest}.
 * Every non-blank filter adds an AND predicate. The base always restricts to non soft-deleted
 * users. Sorting is supplied separately via the {@code Pageable} (not built here).
 */
public final class UserSpecification {

    private UserSpecification() { /* utility */ }

    public static Specification<User> withFilters(UserFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── Always: only active (non-deleted) users ──
            predicates.add(cb.isFalse(root.get("deleted")));

            // ── Free-text q: fullName / email / userCode (case-insensitive contains) ──
            if (filter.getQ() != null && !filter.getQ().isBlank()) {
                String like = "%" + filter.getQ().trim().toLowerCase() + "%";
                Predicate byName = cb.like(cb.lower(root.get("fullName")), like);
                Predicate byEmail = cb.like(cb.lower(root.get("email")), like);
                Predicate byCode = cb.like(cb.lower(root.get("userCode")), like);
                predicates.add(cb.or(byName, byEmail, byCode));
            }

            // ── roleCode: exact match on the joined role ──
            if (filter.getRoleCode() != null && !filter.getRoleCode().isBlank()) {
                Join<User, Role> roleJoin = root.join("role", JoinType.LEFT);
                predicates.add(cb.equal(roleJoin.get("roleCode"), filter.getRoleCode().trim().toUpperCase()));
            }

            // ── status: exact match (silently ignored if not a valid UserStatus) ──
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                UserStatus status = parseStatusOrNull(filter.getStatus());
                if (status != null) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static UserStatus parseStatusOrNull(String raw) {
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
