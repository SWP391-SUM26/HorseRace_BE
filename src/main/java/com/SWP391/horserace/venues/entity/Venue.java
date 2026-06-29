package com.SWP391.horserace.venues.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Maps the {@code venue} (track) table — a structured racing venue reusable by tournaments and races. */
@Entity
@Table(name = "venue")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "venue_id", updatable = false, nullable = false)
    private UUID venueId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "track_name")
    private String trackName;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "surface", columnDefinition = "text")
    private String surface;
}
