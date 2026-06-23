package com.SWP391.horserace.races.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One row of a race's prize distribution (e.g. place "1st", amount 340260). */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrizeDistributionItem {

    @Column(name = "place", nullable = false, length = 20)
    private String place;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
}
