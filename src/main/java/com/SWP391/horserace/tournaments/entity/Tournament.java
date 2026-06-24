package com.SWP391.horserace.tournaments.entity;

import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Maps the {@code tournament} table. */
@Entity
@Table(name = "tournament")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tournament_id", updatable = false, nullable = false)
    private UUID tournamentId;

    @Column(name = "tournament_code", nullable = false, unique = true, length = 50)
    private String tournamentCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "registration_open_at")
    private OffsetDateTime registrationOpenAt;

    @Column(name = "registration_close_at")
    private OffsetDateTime registrationCloseAt;

    @Column(name = "location")
    private String location;

    /** Graded circuit tier (GROUP_1 | GROUP_2 | GROUP_3 | LISTED). Nullable. */
    @Enumerated(EnumType.STRING)
    @Column(name = "circuit_tier", length = 50)
    private CircuitTier circuitTier;

    @Column(name = "total_purse")
    private BigDecimal totalPurse;

    @Column(name = "entry_cap")
    private Integer entryCap;

    /** Eligibility criteria stored as 3 nullable columns (see {@link EligibilityCriteria}). */
    @Embedded
    private EligibilityCriteria eligibility;

    /**
     * CANONICAL TOURNAMENT STATUS SET (must match the schema CHECK on tournament.status):
     * DRAFT → PUBLISHED → REGISTRATION_OPEN → REGISTRATION_CLOSED → ONGOING → COMPLETED, plus CANCELLED.
     * Transitions: publish (DRAFT→PUBLISHED), open-registration (PUBLISHED→REGISTRATION_OPEN),
     * close-registration (REGISTRATION_OPEN/PUBLISHED→REGISTRATION_CLOSED),
     * start (REGISTRATION_CLOSED→ONGOING), complete (ONGOING→COMPLETED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TournamentStatus status = TournamentStatus.DRAFT;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TournamentVenue> venues = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
