package com.SWP391.horserace.venues.service.impl;

import com.SWP391.horserace.venues.dto.VenueResponse;
import com.SWP391.horserace.venues.entity.Venue;
import com.SWP391.horserace.venues.repository.VenueRepository;
import com.SWP391.horserace.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;

    @Override
    public List<VenueResponse> getAllVenues() {
        return venueRepository.findAll().stream()
                .sorted(Comparator.comparing(Venue::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::toResponse)
                .toList();
    }

    private VenueResponse toResponse(Venue v) {
        return VenueResponse.builder()
                .venueId(v.getVenueId())
                .name(v.getName())
                .trackName(v.getTrackName())
                .city(v.getCity())
                .country(v.getCountry())
                .capacity(v.getCapacity())
                .surface(v.getSurface())
                .build();
    }
}
