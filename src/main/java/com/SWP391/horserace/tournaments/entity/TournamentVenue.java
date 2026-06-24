package com.SWP391.horserace.tournaments.entity;

import com.SWP391.horserace.venues.entity.Venue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Maps the {@code tournament_venue} join table — links a tournament to one-or-many structured venues.
 * UNIQUE(tournament_id, venue_id) prevents linking the same venue twice.
 */
@Entity
@Table(name = "tournament_venue",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "venue_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentVenue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tournament_venue_id", updatable = false, nullable = false)
    private UUID tournamentVenueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;
}
