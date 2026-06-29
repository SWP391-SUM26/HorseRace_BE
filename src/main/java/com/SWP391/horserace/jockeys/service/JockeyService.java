package com.SWP391.horserace.jockeys.service;

import com.SWP391.horserace.jockeys.dto.InvitationInsightsResponse;
import com.SWP391.horserace.jockeys.dto.JockeyFilterRequest;
import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.dto.JockeyStatsResponse;
import com.SWP391.horserace.jockeys.dto.JockeySuggestionResponse;
import com.SWP391.horserace.jockeys.dto.UnassignedEntryResponse;
import com.SWP391.horserace.jockeys.dto.UpdateJockeyProfileRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface JockeyService {

    /** List all active jockey profiles (ordered by win count descending). */
    List<JockeyResponse> getAllJockeys();

    /** Get a single jockey profile by the jockey's user id. */
    JockeyResponse getJockeyById(UUID jockeyUserId);

    /** Search jockeys by keyword (name, email, license, userCode). */
    List<JockeyResponse> searchJockeys(String keyword);

    /** Filter jockeys by multiple optional criteria (experience, weight, height, wins, etc.). */
    List<JockeyResponse> filterJockeys(JockeyFilterRequest filter);

    /** Paginated listing of jockeys with configurable sorting. */
    Page<JockeyResponse> getJockeysPaginated(int page, int size, String sortBy, String sortDir);

    /**
     * The given owner's race entries that have no accepted jockey yet
     * (Jockey Market left column). Requires a non-null owner id.
     */
    List<UnassignedEntryResponse> getUnassignedEntries(UUID ownerUserId);

    /**
     * Deterministic compatibility scores (50–99) for every active jockey against the
     * given race + horse, sorted descending. Validates the race and horse exist.
     */
    List<JockeySuggestionResponse> getJockeySuggestions(UUID raceId, UUID horseId);

    /**
     * Partial-update the caller's own jockey profile (FE-v2 jockey contract #8).
     * Only non-null request fields are applied. Throws {@code JOCKEY_NOT_FOUND}
     * if the caller has no profile, {@code UNAUTHENTICATED} if {@code callerUserId} is null.
     */
    JockeyResponse updateMyProfile(UUID callerUserId, UpdateJockeyProfileRequest request);

    /**
     * Aggregated performance + earnings stats for the caller (FE-v2 jockey contract #1),
     * computed over the caller's ACCEPTED rides that have a recorded result.
     */
    JockeyStatsResponse getMyStats(UUID callerUserId);

    /** Invitation analytics for the caller (FE-v2 jockey contract #11). */
    InvitationInsightsResponse getMyInvitationInsights(UUID callerUserId);
}

