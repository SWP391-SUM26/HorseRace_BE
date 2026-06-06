package com.SWP391.horserace.jockeys.repository;

import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JockeyProfileRepository extends JpaRepository<JockeyProfile, UUID>,
        JpaSpecificationExecutor<JockeyProfile> {

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

  /**
   * Search jockeys by keyword (case-insensitive, partial match) against
   * fullName, email, userCode, and licenseNo. Only returns active users.
   */
  @Query("""
      SELECT jp FROM JockeyProfile jp
        JOIN FETCH jp.jockeyUser u
        JOIN FETCH u.role
       WHERE u.deleted = false
         AND (LOWER(u.fullName)  LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(u.userCode)  LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(jp.licenseNo) LIKE LOWER(CONCAT('%', :keyword, '%')))
       ORDER BY jp.winCount DESC
      """)
  List<JockeyProfile> searchByKeyword(String keyword);

  /**
   * Paginated listing of active jockey profiles with eager-fetched User and Role.
   * Sorting is driven by the Pageable parameter.
   */
  @Query(value = """
      SELECT jp FROM JockeyProfile jp
        JOIN FETCH jp.jockeyUser u
        JOIN FETCH u.role
       WHERE u.deleted = false
      """,
      countQuery = """
      SELECT COUNT(jp) FROM JockeyProfile jp
        JOIN jp.jockeyUser u
       WHERE u.deleted = false
      """)
  Page<JockeyProfile> findAllActiveJockeysPaged(Pageable pageable);
}
