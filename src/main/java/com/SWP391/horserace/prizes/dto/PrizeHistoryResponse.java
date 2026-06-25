package com.SWP391.horserace.prizes.dto;

import com.SWP391.horserace.prizes.entity.BeneficiaryType;
import com.SWP391.horserace.prizes.entity.PrizeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrizeHistoryResponse {
    private UUID prizeId;
    private UUID tournamentId;
    private UUID raceId;
    private String prizeCode;
    private BeneficiaryType beneficiaryType;
    private Integer rankPosition;
    private BigDecimal prizeAmount;
    private String currencyCode;
    private PrizeStatus status;
    private OffsetDateTime createdAt;
}
