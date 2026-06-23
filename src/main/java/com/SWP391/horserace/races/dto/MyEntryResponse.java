package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.RaceEntryStatus;
import lombok.Builder;
import lombok.Data;

/** The caller-owner's own entry in a race (the "Your Horse Status" card). */
@Data
@Builder
public class MyEntryResponse {
    private String horseName;
    private String drawStall;
    private String jockeyName;
    private Integer weightCarriedLbs;
    private RaceEntryStatus entryStatus;
}
