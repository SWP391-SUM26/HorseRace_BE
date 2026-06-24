package com.SWP391.horserace.races.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One split (fraction) time of a race — FE-v2 Results (mục 5). Child of {@code race_fraction}. */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceFraction {

    @Column(name = "split_no", nullable = false)
    private Integer splitNo;

    @Column(name = "time_ms", nullable = false)
    private Long timeMs;
}
