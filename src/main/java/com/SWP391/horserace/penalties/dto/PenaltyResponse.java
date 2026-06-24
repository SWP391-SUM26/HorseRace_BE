package com.SWP391.horserace.penalties.dto;

import com.SWP391.horserace.penalties.entity.PenaltyStatus;
import com.SWP391.horserace.penalties.entity.PenaltyType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Read view of a {@code penalty} row (FE-v2 §4/5/7 — rulings / pending incidents). */
@Data
@Builder
public class PenaltyResponse {
    private UUID penaltyId;
    private UUID entryId;
    private String horseName;
    private PenaltyType penaltyType;
    private Long timePenaltyMs;
    private BigDecimal fineAmount;
    private PenaltyStatus status;
    private String issuedByName;
}
