package com.SWP391.horserace.horses.dto;

import lombok.Data;

import java.util.UUID;

/**
 * Query parameters for the horse list endpoint — combines search, filter, sort and pagination
 * (bound from the query string via {@code @ModelAttribute}).
 */
@Data
public class HorseFilterRequest {

    /** Free-text search across horse code, name and microchip number. */
    private String q;

    private String status;       // ACTIVE | RETIRED | INACTIVE
    private String gender;       // MALE | FEMALE | GELDING
    private String breed;        // partial match
    private UUID ownerUserId;    // exact owner

    private String sortBy;       // name | horseCode | createdAt | dateOfBirth | status (default createdAt)
    private String sortDir;      // asc | desc (default desc)
    private Integer page;        // default 0
    private Integer size;        // default 10
}
