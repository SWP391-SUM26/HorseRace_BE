package com.SWP391.horserace.prizes.service.impl;

import com.SWP391.horserace.prizes.dto.PrizeHistoryResponse;
import com.SWP391.horserace.prizes.entity.Prize;
import com.SWP391.horserace.prizes.repository.PrizeRepository;
import com.SWP391.horserace.prizes.service.PrizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrizeServiceImpl implements PrizeService {

    private final PrizeRepository prizeRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<PrizeHistoryResponse> getPrizeHistory(Pageable pageable) {
        return prizeRepository.findAll(pageable).map(this::mapToResponse);
    }

    private PrizeHistoryResponse mapToResponse(Prize prize) {
        return PrizeHistoryResponse.builder()
                .prizeId(prize.getPrizeId())
                .tournamentId(prize.getTournament() != null ? prize.getTournament().getTournamentId() : null)
                .raceId(prize.getRace() != null ? prize.getRace().getRaceId() : null)
                .prizeCode(prize.getPrizeCode())
                .beneficiaryType(prize.getBeneficiaryType())
                .rankPosition(prize.getRankPosition())
                .prizeAmount(prize.getPrizeAmount())
                .currencyCode(prize.getCurrencyCode())
                .status(prize.getStatus())
                .createdAt(prize.getCreatedAt())
                .build();
    }
}
