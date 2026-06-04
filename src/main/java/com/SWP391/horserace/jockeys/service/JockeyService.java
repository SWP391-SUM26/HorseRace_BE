package com.SWP391.horserace.jockeys.service;

import com.SWP391.horserace.jockeys.dto.JockeyResponse;

import java.util.List;
import java.util.UUID;

public interface JockeyService {

    /** List all active jockey profiles (ordered by win count descending). */
    List<JockeyResponse> getAllJockeys();

    /** Get a single jockey profile by the jockey's user id. */
    JockeyResponse getJockeyById(UUID jockeyUserId);
}
