package com.SWP391.horserace.tournaments.service;

import com.SWP391.horserace.tournaments.dto.TournamentFilterRequest;
import com.SWP391.horserace.tournaments.dto.TournamentRequest;
import com.SWP391.horserace.tournaments.dto.TournamentResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface TournamentService {
    TournamentResponse createTournament(TournamentRequest request, UUID userId);
    Page<TournamentResponse> getTournaments(TournamentFilterRequest filter);
    TournamentResponse getTournamentById(UUID id);
    TournamentResponse updateTournament(UUID id, TournamentRequest request);
    void deleteTournament(UUID id);
    TournamentResponse publishTournament(UUID id);
    TournamentResponse closeRegistration(UUID id);

    // §C5 — status transitions
    TournamentResponse openRegistration(UUID id);
    TournamentResponse startTournament(UUID id);
    TournamentResponse completeTournament(UUID id);
}
