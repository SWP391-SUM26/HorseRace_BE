package com.SWP391.horserace.inspections.repository;

import com.SWP391.horserace.inspections.entity.DocumentReviewStatus;
import com.SWP391.horserace.inspections.entity.EntryDocumentReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntryDocumentReviewRepository extends JpaRepository<EntryDocumentReview, UUID> {

    /** The (single) document review for an entry, if it has been reviewed. */
    Optional<EntryDocumentReview> findByEntry_EntryId(UUID entryId);

    /** All document reviews that exist for the given race. */
    List<EntryDocumentReview> findByRace_RaceId(UUID raceId);

    /** All document reviews that exist for the given entries (merge with the full entry list). */
    List<EntryDocumentReview> findByEntry_EntryIdIn(Collection<UUID> entryIds);

    /** Count reviews in a race with a specific document status (used by the pre-race gate). */
    long countByRace_RaceIdAndDocumentStatus(UUID raceId, DocumentReviewStatus documentStatus);
}
