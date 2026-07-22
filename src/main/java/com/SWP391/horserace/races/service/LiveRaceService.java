package com.SWP391.horserace.races.service;

import com.SWP391.horserace.races.dto.LiveRaceResponse;

import java.util.UUID;

/** Live race monitor — polling snapshot (FE-v2 §4). */
public interface LiveRaceService {

    LiveRaceResponse getLive(UUID raceId);

    /**
     * Writes live telemetry. {@code callerId} is required: reads are open to any authenticated
     * user, but a write must come from an ADMIN or a referee CONFIRMED on this very race —
     * the running order feeds the leaderboard spectators bet against.
     */
    void updateLivePositions(UUID callerId, UUID raceId,
                             com.SWP391.horserace.races.dto.UpdateLivePositionRequest request);

    java.util.List<LiveRaceResponse.RunnerRow> getLiveLeaderboard(UUID raceId);
}
