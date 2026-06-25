package com.SWP391.horserace.prizes.service;

import com.SWP391.horserace.prizes.dto.PrizeHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PrizeService {
    Page<PrizeHistoryResponse> getPrizeHistory(Pageable pageable);
}
