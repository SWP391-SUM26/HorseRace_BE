package com.SWP391.horserace.jockeys.repository;

import com.SWP391.horserace.jockeys.dto.JockeyFilterRequest;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a dynamic WHERE clause from {@link JockeyFilterRequest} parameters.
 * Every non-null filter field adds an AND predicate. The base always includes
 * {@code u.deleted = false} so only active users are returned.
 */
public final class JockeyProfileSpecification {

    private JockeyProfileSpecification() { /* utility */ }

    public static Specification<JockeyProfile> withFilters(JockeyFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Eagerly join the user to avoid N+1 and to filter on user fields
            Join<JockeyProfile, User> userJoin = root.join("jockeyUser", JoinType.INNER);

            // Eagerly fetch user + role to keep the same behavior as existing queries
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                // Avoid fetch for count queries
                root.fetch("jockeyUser", JoinType.INNER).fetch("role", JoinType.INNER);
            }

            // ── Always: only active (non-deleted) users ──
            predicates.add(cb.equal(userJoin.get("deleted"), false));

            // ── User fields ──
            if (filter.getFullName() != null && !filter.getFullName().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(userJoin.get("fullName")),
                        "%" + filter.getFullName().trim().toLowerCase() + "%"));
            }

            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(userJoin.get("email")),
                        "%" + filter.getEmail().trim().toLowerCase() + "%"));
            }

            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(userJoin.get("status").as(String.class)),
                        filter.getStatus().trim().toUpperCase()));
            }

            // ── Jockey profile fields ──
            if (filter.getLicenseNo() != null && !filter.getLicenseNo().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("licenseNo")),
                        "%" + filter.getLicenseNo().trim().toLowerCase() + "%"));
            }

            // Experience range
            if (filter.getMinExperienceYrs() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("experienceYrs"), filter.getMinExperienceYrs()));
            }
            if (filter.getMaxExperienceYrs() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("experienceYrs"), filter.getMaxExperienceYrs()));
            }

            // Body weight range
            if (filter.getMinBodyWeight() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bodyWeight"), filter.getMinBodyWeight()));
            }
            if (filter.getMaxBodyWeight() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bodyWeight"), filter.getMaxBodyWeight()));
            }

            // Height range
            if (filter.getMinHeightCm() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("heightCm"), filter.getMinHeightCm()));
            }
            if (filter.getMaxHeightCm() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("heightCm"), filter.getMaxHeightCm()));
            }

            // Win count range
            if (filter.getMinWinCount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("winCount"), filter.getMinWinCount()));
            }
            if (filter.getMaxWinCount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("winCount"), filter.getMaxWinCount()));
            }

            // ── Sorting ──
            String sortField = resolveSortField(filter.getSortBy());
            boolean ascending = "asc".equalsIgnoreCase(filter.getSortDir());

            if (sortField.startsWith("user.")) {
                // Sort on a user field
                String userField = sortField.substring(5);
                query.orderBy(ascending
                        ? cb.asc(userJoin.get(userField))
                        : cb.desc(userJoin.get(userField)));
            } else {
                query.orderBy(ascending
                        ? cb.asc(root.get(sortField))
                        : cb.desc(root.get(sortField)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "winCount";
        }
        return switch (sortBy.trim().toLowerCase()) {
            case "experience", "experienceyrs" -> "experienceYrs";
            case "weight", "bodyweight" -> "bodyWeight";
            case "height", "heightcm" -> "heightCm";
            case "name", "fullname" -> "user.fullName";
            case "wincount" -> "winCount";
            default -> "winCount";
        };
    }
}
