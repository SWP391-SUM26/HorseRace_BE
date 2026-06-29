package com.SWP391.horserace.venues.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Structured venue (track) projection, reused by tournament and race responses. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueResponse {
    private UUID venueId;
    private String name;
    private String trackName;
    private String city;
    private String country;
    private Integer capacity;
    private String surface;
}
