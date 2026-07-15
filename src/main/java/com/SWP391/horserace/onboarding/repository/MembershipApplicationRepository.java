package com.SWP391.horserace.onboarding.repository;

import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.MembershipApplication;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipApplicationRepository
        extends JpaRepository<MembershipApplication, UUID>,
        JpaSpecificationExecutor<MembershipApplication> {

    /** Pending-approvals count for the stats card. */
    long countByStatus(ApplicationStatus status);

    /** Decisions of a given status since the start of the server day (today-scoped stats). */
    long countByStatusAndReviewedAtGreaterThanEqual(ApplicationStatus status, OffsetDateTime startOfDay);

    /** Applicant history — previous applications by the same email (newest first). */
    List<MembershipApplication> findByEmailOrderBySubmittedAtDesc(String email);

    /**
     * The most-recent application for the given email + requested role, regardless of status.
     * Status-AGNOSTIC on purpose: the caller applies the decidable/terminal check afterwards, so an
     * already-decided application still resolves here (→ APPLICATION_ALREADY_DECIDED) instead of
     * being wrongly reported as APPLICATION_NOT_FOUND.
     */
    Optional<MembershipApplication> findFirstByEmailAndRequestedRoleOrderBySubmittedAtDesc(
            String email, RequestedRole requestedRole);
}
