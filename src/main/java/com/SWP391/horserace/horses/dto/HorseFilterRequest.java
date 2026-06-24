package com.SWP391.horserace.horses.dto;

import com.SWP391.horserace.horses.entity.HorseGender;
import com.SWP391.horserace.horses.entity.HorseStatus;
import lombok.Data;

/**
 * Query parameters for the horse list endpoint — combines search, filter, sort and pagination
 * (bound from the query string via {@code @ModelAttribute}). Enum filters are converted from the
 * query string by Spring (e.g. {@code ?status=ACTIVE}).
 */
@Data
public class HorseFilterRequest {

    /** Free-text search across horse code, name and microchip number. */
    private String q;

    private HorseStatus status;
    private HorseGender gender;
    private String breed;        // partial match
    private String ownerUserId;  // exact owner — a UUID string, or the literal "me" (current user)

    private String sortBy;       // name | horseCode | createdAt | dateOfBirth | status (default createdAt)
    private String sortDir;      // asc | desc (default desc)
    private Integer page;        // default 0
    private Integer size;        // default 10
}
