package com.SWP391.horserace.venues.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.venues.dto.VenueResponse;
import com.SWP391.horserace.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    /** GET /api/v1/venues — all venues (reference data for race/tournament forms). */
    @GetMapping
    public ApiResponse<List<VenueResponse>> listVenues() {
        return ApiResponse.<List<VenueResponse>>builder()
                .success(true)
                .message("Fetched venues")
                .data(venueService.getAllVenues())
                .build();
    }
}
