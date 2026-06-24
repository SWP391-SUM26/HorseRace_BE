package com.SWP391.horserace.inspections.repository;

import com.SWP391.horserace.inspections.entity.RaceEntryInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaceEntryInspectionRepository extends JpaRepository<RaceEntryInspection, UUID> {

    /** The (single) inspection for an entry, if it has been inspected. */
    Optional<RaceEntryInspection> findByEntry_EntryId(UUID entryId);

    /** Inspection for an entry with inspectedBy eagerly fetched (for building the response). */
    @Query("""
        SELECT i FROM RaceEntryInspection i
          LEFT JOIN FETCH i.inspectedBy u
         WHERE i.entry.entryId = :entryId
        """)
    Optional<RaceEntryInspection> findByEntryIdWithUser(@Param("entryId") UUID entryId);

    /** All inspections that exist for the given entries (used to merge with the full entry list). */
    List<RaceEntryInspection> findByEntry_EntryIdIn(Collection<UUID> entryIds);
}
