package com.SWP391.horserace.referee.service;

import com.SWP391.horserace.referee.dto.RefereeDashboardResponse;

import java.util.UUID;

/** Builds the referee dashboard aggregate (FE-v2 §1). */
public interface RefereeDashboardService {

    /**
     * Aggregate dashboard for the authenticated referee.
     *
     * @param userId authenticated referee id ({@code null} → UNAUTHENTICATED)
     * @param raceId optional — when given, that race is the "next race"; otherwise the soonest
     *               SCHEDULED/OPEN race by scheduledStartAt is used.
     */
    RefereeDashboardResponse getDashboard(UUID userId, UUID raceId);
}
