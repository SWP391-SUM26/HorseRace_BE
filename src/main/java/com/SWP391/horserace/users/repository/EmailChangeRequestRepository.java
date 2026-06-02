package com.SWP391.horserace.users.repository;

import com.SWP391.horserace.users.entity.EmailChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, UUID> {

    /** Lookup a pending request by the hash of the presented code. */
    Optional<EmailChangeRequest> findByCodeHashAndConsumedFalse(String codeHash);

    /** Invalidate any outstanding (unconsumed) requests for a user before issuing a new one. */
    long deleteByUser_UserIdAndConsumedFalse(UUID userId);
}
