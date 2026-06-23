package com.SWP391.horserace.horses.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Pedigree of a horse: sire, dam and trainer (FE-v2 Horse Profile, mục 1). */
@Data
@Builder
public class PedigreeResponse {

    private Sire sire;
    private Dam dam;
    private Trainer trainer;

    @Data
    @Builder
    public static class Sire {
        private String name;
        private Integer wins;
        private BigDecimal earnings;
    }

    @Data
    @Builder
    public static class Dam {
        private String name;
        private Integer wins;
        private String note;
    }

    @Data
    @Builder
    public static class Trainer {
        private String name;
        private String licenseNo;
    }
}
