package com.SWP391.horserace.assignments.entity;

import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps {@code tournament_referee_assignment} — a referee invited to officiate a whole tournament
 * (an invite/accept flow, distinct from the per-race {@link RefereeAssignment}).
 */
@Entity
@Table(name = "tournament_referee_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRefereeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tournament_ref_assignment_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_user_id")
    private User referee;

    /** CHIEF | JUDGE | STEWARD | TIMEKEEPER | OBSERVER */
    @Enumerated(EnumType.STRING)
    @Column(name = "panel_role", length = 50)
    private PanelRole panelRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TournamentRefereeStatus status = TournamentRefereeStatus.INVITED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Column(name = "invited_at")
    private OffsetDateTime invitedAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}
