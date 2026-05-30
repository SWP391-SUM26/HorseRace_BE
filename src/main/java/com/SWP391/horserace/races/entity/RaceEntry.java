package com.SWP391.horserace.races.entity;

import com.SWP391.horserace.registrations.entity.TournamentRegistration;
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

/** Maps the {@code race_entry} table (a horse registration assigned to a specific race). */
@Entity
@Table(name = "race_entry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "entry_id", updatable = false, nullable = false)
    private UUID entryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id")
    private TournamentRegistration registration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @Column(name = "entry_code", nullable = false, unique = true, length = 50)
    private String entryCode;

    @Column(name = "entry_no")
    private Integer entryNo;

    @Column(name = "lane_no")
    private Integer laneNo;

    /** ENTERED | CHECKED_IN | SCRATCHED | DISQUALIFIED | FINISHED */
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ENTERED";

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
