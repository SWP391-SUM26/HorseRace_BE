package com.SWP391.horserace.venues.service;

import com.SWP391.horserace.venues.dto.VenueResponse;

import java.util.List;

public interface VenueService {

    /** All venues (tracks), ordered by name — reference data for race/tournament forms. */
    List<VenueResponse> getAllVenues();
}
