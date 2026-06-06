package com.SWP391.horserace.assignments.repository;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JockeyAssignmentRepository extends JpaRepository<JockeyAssignment, UUID> {

    /** Check if a race entry already has a jockey assigned (any non-CANCELLED status). */
    @Query("""
        SELECT COUNT(ja) > 0 FROM JockeyAssignment ja
         WHERE ja.entry.entryId = :entryId
           AND ja.status <> 'CANCELLED'
           AND ja.status <> 'DECLINED'
        """)
    boolean existsActiveByEntryId(@Param("entryId") UUID entryId);

    /** Find assignment by id with all associations eagerly fetched (avoids N+1). */
    @Query("""
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.entry e
          JOIN FETCH e.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
          JOIN FETCH race.tournament t
          JOIN FETCH ja.jockey j
         WHERE ja.assignmentId = :assignmentId
        """)
    Optional<JockeyAssignment> findByIdWithDetails(@Param("assignmentId") UUID assignmentId);

    /** List all assignments for a specific jockey, paginated. */
    @Query(value = """
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.entry e
          JOIN FETCH e.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
          JOIN FETCH race.tournament t
          JOIN FETCH ja.jockey j
         WHERE j.userId = :jockeyUserId
        """,
        countQuery = """
        SELECT COUNT(ja) FROM JockeyAssignment ja
         WHERE ja.jockey.userId = :jockeyUserId
        """)
    Page<JockeyAssignment> findByJockeyUserId(@Param("jockeyUserId") UUID jockeyUserId, Pageable pageable);

    /** List all assignments for a specific jockey filtered by status, paginated. */
    @Query(value = """
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.entry e
          JOIN FETCH e.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
          JOIN FETCH race.tournament t
          JOIN FETCH ja.jockey j
         WHERE j.userId = :jockeyUserId
           AND ja.status = :status
        """,
        countQuery = """
        SELECT COUNT(ja) FROM JockeyAssignment ja
         WHERE ja.jockey.userId = :jockeyUserId
           AND ja.status = :status
        """)
    Page<JockeyAssignment> findByJockeyUserIdAndStatus(
            @Param("jockeyUserId") UUID jockeyUserId,
            @Param("status") String status,
            Pageable pageable);

    /** List all assignments sent by a specific owner, paginated. */
    @Query(value = """
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.entry e
          JOIN FETCH e.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
          JOIN FETCH race.tournament t
          JOIN FETCH ja.jockey j
         WHERE o.userId = :ownerUserId
        """,
        countQuery = """
        SELECT COUNT(ja) FROM JockeyAssignment ja
          JOIN ja.entry e
          JOIN e.registration r
         WHERE r.owner.userId = :ownerUserId
        """)
    Page<JockeyAssignment> findByOwnerUserId(@Param("ownerUserId") UUID ownerUserId, Pageable pageable);

    /** List all assignments sent by a specific owner filtered by status, paginated. */
    @Query(value = """
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.entry e
          JOIN FETCH e.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
          JOIN FETCH race.tournament t
          JOIN FETCH ja.jockey j
         WHERE o.userId = :ownerUserId
           AND ja.status = :status
        """,
        countQuery = """
        SELECT COUNT(ja) FROM JockeyAssignment ja
          JOIN ja.entry e
          JOIN e.registration r
         WHERE r.owner.userId = :ownerUserId
           AND ja.status = :status
        """)
    Page<JockeyAssignment> findByOwnerUserIdAndStatus(
            @Param("ownerUserId") UUID ownerUserId,
            @Param("status") String status,
            Pageable pageable);

    /** List all assignments filtered by status only, paginated. */
    @Query(value = """
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.entry e
          JOIN FETCH e.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
          JOIN FETCH race.tournament t
          JOIN FETCH ja.jockey j
         WHERE ja.status = :status
        """,
        countQuery = """
        SELECT COUNT(ja) FROM JockeyAssignment ja
         WHERE ja.status = :status
        """)
    Page<JockeyAssignment> findByStatus(@Param("status") String status, Pageable pageable);

    /** List all assignments, paginated with full fetch. */
    @Query(value = """
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.entry e
          JOIN FETCH e.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
          JOIN FETCH race.tournament t
          JOIN FETCH ja.jockey j
        """,
        countQuery = "SELECT COUNT(ja) FROM JockeyAssignment ja")
    Page<JockeyAssignment> findAllWithDetails(Pageable pageable);
}
