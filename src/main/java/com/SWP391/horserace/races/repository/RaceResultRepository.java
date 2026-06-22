package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

    /** Results for a set of entries — used to fill finishPosition into a horse's race history. */
    List<RaceResult> findByEntry_EntryIdIn(Collection<UUID> entryIds);
}
