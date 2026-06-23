package com.SWP391.horserace.assignments.repository;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import com.SWP391.horserace.races.entity.RaceEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JockeyAssignmentRepository extends JpaRepository<JockeyAssignment, UUID> {

    /** Check if a race entry already has a jockey assigned (any non-CANCELLED status). */
    @Query("""
        SELECT COUNT(ja) > 0 FROM JockeyAssignment ja
         WHERE ja.entry.entryId = :entryId
           AND ja.status <> com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus.CANCELLED
           AND ja.status <> com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus.DECLINED
        """)
    boolean existsActiveByEntryId(@Param("entryId") UUID entryId);

    /**
     * The ACCEPTED jockey assignment for a single entry (if any), jockey eagerly fetched.
     * Used to resolve the riding jockey's name for one race entry.
     */
    @Query("""
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.jockey j
         WHERE ja.entry.entryId = :entryId
           AND ja.status = com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus.ACCEPTED
        """)
    Optional<JockeyAssignment> findAcceptedByEntryId(@Param("entryId") UUID entryId);

    /**
     * All ACCEPTED jockey assignments for a set of entries, jockey eagerly fetched.
     * Used to resolve riding jockey names for a whole race's entries in one query (avoids N+1).
     */
    @Query("""
        SELECT ja FROM JockeyAssignment ja
          JOIN FETCH ja.jockey j
         WHERE ja.entry.entryId IN :entryIds
           AND ja.status = com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus.ACCEPTED
        """)
    List<JockeyAssignment> findAcceptedByEntryIds(@Param("entryIds") Collection<UUID> entryIds);

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
            @Param("status") JockeyAssignmentStatus status,
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
            @Param("status") JockeyAssignmentStatus status,
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
    Page<JockeyAssignment> findByStatus(@Param("status") JockeyAssignmentStatus status, Pageable pageable);

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

    /**
     * An owner's race entries that have NO accepted jockey yet (Jockey Market, FE-v2 §2).
     *
     * <p>Walks {@code tournament_registration[owner] → race_entry → race}, eagerly fetching
     * the horse and race, and excludes any entry that already has a
     * {@link JockeyAssignmentStatus#ACCEPTED} assignment.
     */
    @Query("""
        SELECT e FROM RaceEntry e
          JOIN FETCH e.registration r
          JOIN FETCH r.horse h
          JOIN FETCH e.race race
         WHERE r.owner.userId = :ownerUserId
           AND race.deleted = false
           AND NOT EXISTS (
                SELECT 1 FROM JockeyAssignment ja
                 WHERE ja.entry = e
                   AND ja.status = com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus.ACCEPTED
           )
         ORDER BY race.scheduledStartAt ASC
        """)
    List<RaceEntry> findUnassignedEntriesByOwner(@Param("ownerUserId") UUID ownerUserId);
}
