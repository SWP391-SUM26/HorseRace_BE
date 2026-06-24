package com.SWP391.horserace.races.service;

import com.SWP391.horserace.races.dto.LiveRaceResponse;

import java.util.UUID;

/** Live race monitor — polling snapshot (FE-v2 §4). */
public interface LiveRaceService {

    LiveRaceResponse getLive(UUID raceId);
}
