package com.SWP391.horserace.horses.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Career statistics for a horse (FE-v2 Horse Profile, mục 1). */
@Data
@Builder
public class HorseStatsResponse {
    /** Sum of prize_earned across all of the horse's race entries. */
    private BigDecimal lifetimeEarnings;
    /** Number of races the horse has a recorded result for. */
    private long starts;
    /** Results with finish_position = 1. */
    private int wins;
    /** Results with finish_position <= 3. */
    private int top3;
    /** Grade enum, e.g. GRADE_1 (stored column). */
    private String grade;
    /** Characteristic tags, e.g. ["EARLY_SPRINTER", "FIRM_TURF"]. */
    private List<String> characteristics;
}
