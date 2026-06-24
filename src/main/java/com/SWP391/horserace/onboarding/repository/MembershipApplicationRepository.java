package com.SWP391.horserace.onboarding.repository;

import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.MembershipApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
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
}
