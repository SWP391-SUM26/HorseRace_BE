package com.SWP391.horserace.standings.service.impl;

import com.SWP391.horserace.standings.dto.LeaderboardEntryResponse;
import com.SWP391.horserace.standings.repository.StandingsRepository;
import com.SWP391.horserace.standings.service.StandingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class StandingsServiceImpl implements StandingsService {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 10;

    private final StandingsRepository standingsRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> rankJockeys(int limit) {
        AtomicInteger rank = new AtomicInteger();
        return standingsRepository.rankJockeys(page(limit)).stream()
                .map(r -> LeaderboardEntryResponse.builder()
                        .rank(rank.incrementAndGet())
                        .jockeyUserId(r.getJockeyUserId())
                        .name(r.getName())
                        .code(initials(r.getName()))
                        .wins(r.getWins())
                        .starts(r.getStarts())
                        .earnings(nz(r.getEarnings()))
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> rankHorses(UUID tournamentId, UUID ownerUserId, int limit) {
        AtomicInteger rank = new AtomicInteger();
        return standingsRepository.rankHorses(tournamentId, ownerUserId, page(limit)).stream()
                .map(r -> LeaderboardEntryResponse.builder()
                        .rank(rank.incrementAndGet())
                        .jockeyUserId(r.getHorseId())
                        .name(r.getName())
                        // horses get their stable code as the chip rather than initials
                        .code(r.getHorseCode())
                        .wins(r.getWins())
                        .starts(r.getStarts())
                        .earnings(nz(r.getEarnings()))
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> rankPredictors(int limit) {
        AtomicInteger rank = new AtomicInteger();
        return standingsRepository.rankPredictors(page(limit)).stream()
                .map(r -> LeaderboardEntryResponse.builder()
                        .rank(rank.incrementAndGet())
                        .jockeyUserId(r.getUserId())
                        .name(r.getName())
                        .code(initials(r.getName()))
                        .wins(r.getWins())
                        .starts(r.getSettled())
                        .earnings(nz(r.getWinnings()))
                        .build())
                .toList();
    }

    private Pageable page(int limit) {
        int n = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return PageRequest.of(0, n);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** "Lý Tuấn Kiệt" → "LK": first + last initial, matching the FE's own chip helper. */
    private static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "—";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
