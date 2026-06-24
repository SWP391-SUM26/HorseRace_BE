package com.SWP391.horserace.races.service;

import com.SWP391.horserace.races.dto.AssignParticipantRequest;
import com.SWP391.horserace.races.dto.MyEntryResponse;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.dto.RaceFilterRequest;
import com.SWP391.horserace.races.dto.RaceRequest;
import com.SWP391.horserace.races.dto.RaceResponse;
import com.SWP391.horserace.races.dto.RaceStatsResponse;
import com.SWP391.horserace.races.dto.ScheduleRaceRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface RaceService {

    Page<RaceResponse> listRaces(RaceFilterRequest filter);

    /** §D3 — count-by-status KPIs, optionally scoped to one tournament. */
    RaceStatsResponse getRaceStats(UUID tournamentId);

    RaceResponse getRaceById(UUID id);

    RaceResponse createRace(UUID currentUserId, RaceRequest request);

    RaceResponse updateRace(UUID currentUserId, UUID id, RaceRequest request);

    void deleteRace(UUID currentUserId, UUID id);

    RaceResponse scheduleRace(UUID currentUserId, UUID id, ScheduleRaceRequest request);

    RaceResponse cancelRace(UUID currentUserId, UUID id);

    RaceEntryResponse assignParticipant(UUID currentUserId, UUID raceId, AssignParticipantRequest request);

    List<RaceEntryResponse> listEntries(UUID raceId);

    MyEntryResponse getMyEntry(UUID raceId, UUID ownerUserId);
}
