package com.SWP391.horserace.standings.service;

import com.SWP391.horserace.standings.dto.LeaderboardEntryResponse;

import java.util.List;
import java.util.UUID;

/** League tables computed on the fly from OFFICIAL results — there is no `standing` table. */
public interface StandingsService {

    List<LeaderboardEntryResponse> rankJockeys(int limit);

    List<LeaderboardEntryResponse> rankHorses(UUID tournamentId, UUID ownerUserId, int limit);

    List<LeaderboardEntryResponse> rankPredictors(int limit);
}
