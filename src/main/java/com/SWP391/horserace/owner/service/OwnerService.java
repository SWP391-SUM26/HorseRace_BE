package com.SWP391.horserace.owner.service;

import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.owner.dto.OwnerOverviewResponse;

import java.util.List;
import java.util.UUID;

/** Owner-scoped dashboard reads (FE-v2 be-contracts-todo.md §4). */
public interface OwnerService {

    /** The caller's own horses ("My Stable"). */
    List<HorseResponse> getOwnerHorses(UUID ownerUserId);

    /** Aggregated KPIs + horses + upcoming races for the caller. */
    OwnerOverviewResponse getOverview(UUID ownerUserId);

    /** Distinct IDs of every race the caller's horses are entered into (any status). */
    List<UUID> getOwnerRaceIds(UUID ownerUserId);
}
