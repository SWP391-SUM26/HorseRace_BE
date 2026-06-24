package com.SWP391.horserace.onboarding.repository;

import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.MembershipApplication;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the membership-application queue: status, requestedRole, free-text q. */
public final class MembershipApplicationSpecifications {

    private MembershipApplicationSpecifications() {
    }

    public static Specification<MembershipApplication> hasStatus(ApplicationStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<MembershipApplication> hasRequestedRole(RequestedRole requestedRole) {
        return (root, query, cb) ->
                requestedRole == null ? cb.conjunction() : cb.equal(root.get("requestedRole"), requestedRole);
    }

    /** Case-insensitive match across full_name, email, application_code. */
    public static Specification<MembershipApplication> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("applicationCode")), like));
        };
    }

    public static Specification<MembershipApplication> filter(
            ApplicationStatus status, RequestedRole requestedRole, String q) {
        return Specification.where(hasStatus(status))
                .and(hasRequestedRole(requestedRole))
                .and(matchesQuery(q));
    }
}
