package com.SWP391.horserace.jockeys.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * A jockey suggestion with a deterministic compatibility score (50–99) for a
 * given race + horse pairing (FE-v2 §2):
 * {@code GET /api/v1/races/{raceId}/jockey-suggestions?horseId={horseId}}.
 *
 * <p>No ML model exists; the score is computed from the jockey's stats plus a
 * stable hash of (jockeyUserId + horseId) so it is repeatable. FE merges this
 * into the {@code /jockeys} list by {@code jockeyUserId}.
 */
@Data
@Builder
public class JockeySuggestionResponse {
    private UUID jockeyUserId;
    private int compatibility;
}
