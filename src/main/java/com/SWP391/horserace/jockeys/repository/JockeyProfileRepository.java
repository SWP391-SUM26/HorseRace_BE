package com.SWP391.horserace.jockeys.repository;

import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JockeyProfileRepository extends JpaRepository<JockeyProfile, UUID> {

    /**
     * All jockey profiles whose owning user is active (not soft-deleted).
     * Eagerly fetches the User and its Role to avoid N+1 queries.
     */
    @Query("""
           SELECT jp FROM JockeyProfile jp
             JOIN FETCH jp.jockeyUser u
             JOIN FETCH u.role
            WHERE u.deleted = false
            ORDER BY jp.winCount DESC
           """)
    List<JockeyProfile> findAllActiveJockeys();

    /**
     * A single jockey profile by user id, only if the user is active.
     */
    @Query("""
           SELECT jp FROM JockeyProfile jp
             JOIN FETCH jp.jockeyUser u
             JOIN FETCH u.role
            WHERE jp.jockeyUserId = :jockeyUserId
              AND u.deleted = false
           """)
    Optional<JockeyProfile> findByIdAndUserActive(UUID jockeyUserId);
}
