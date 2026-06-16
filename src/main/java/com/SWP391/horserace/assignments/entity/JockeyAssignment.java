package com.SWP391.horserace.assignments.entity;

import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code jockey_assignment} table (owner invites a jockey to ride an entry). */
@Entity
@Table(name = "jockey_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id", updatable = false, nullable = false)
    private UUID assignmentId;

    /** UNIQUE: one jockey assignment per entry. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", unique = true)
    private RaceEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jockey_user_id")
    private User jockey;

    /** INVITED | ACCEPTED | DECLINED | CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private JockeyAssignmentStatus status = JockeyAssignmentStatus.INVITED;

    @Column(name = "invited_at")
    private OffsetDateTime invitedAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
