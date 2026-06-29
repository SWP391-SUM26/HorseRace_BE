package com.SWP391.horserace.races.dto;

import java.math.BigDecimal;

/** One prize tier in a race's distribution, e.g. { place: "1st", amount: 340260 }. */
public record PrizeDistributionDto(String place, BigDecimal amount) {
}
