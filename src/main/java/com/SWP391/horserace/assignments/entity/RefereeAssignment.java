package com.SWP391.horserace.assignments.entity;

import com.SWP391.horserace.races.entity.Race;
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

/** Maps the {@code referee_assignment} table (a referee assigned to officiate a race). */
@Entity
@Table(name = "referee_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ref_assignment_id", updatable = false, nullable = false)
    private UUID refAssignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_user_id")
    private User referee;

    /** CHIEF | JUDGE | STEWARD | TIMEKEEPER | OBSERVER */
    @Enumerated(EnumType.STRING)
    @Column(name = "panel_role", length = 50)
    private PanelRole panelRole;

    /** ASSIGNED | CONFIRMED | REVOKED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private RefereeAssignmentStatus status = RefereeAssignmentStatus.ASSIGNED;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}
