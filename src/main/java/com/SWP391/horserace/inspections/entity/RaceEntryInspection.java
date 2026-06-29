package com.SWP391.horserace.inspections.entity;

import com.SWP391.horserace.races.entity.Race;
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

/** Maps the {@code race_entry_inspection} table (pre-race clearance per entry — FE-v2 §2). */
@Entity
@Table(name = "race_entry_inspection")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceEntryInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "inspection_id", updatable = false, nullable = false)
    private UUID inspectionId;

    /** One inspection per entry (UNIQUE entry_id). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false, unique = true)
    private RaceEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(name = "health_cert_passed", nullable = false)
    @Builder.Default
    private boolean healthCertPassed = false;

    @Column(name = "weight_verified", nullable = false)
    @Builder.Default
    private boolean weightVerified = false;

    @Column(name = "weight_carried_lbs")
    private Integer weightCarriedLbs;

    @Column(name = "coggins_test_passed", nullable = false)
    @Builder.Default
    private boolean cogginsTestPassed = false;

    @Column(name = "pre_race_exam_passed", nullable = false)
    @Builder.Default
    private boolean preRaceExamPassed = false;

    /** CLEARED | PENDING | VET_CHECK */
    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_status", nullable = false, length = 20)
    @Builder.Default
    private InspectionStatus inspectionStatus = InspectionStatus.PENDING;

    @Column(name = "steward_note", columnDefinition = "text")
    private String stewardNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspected_by_user_id")
    private User inspectedBy;

    @Column(name = "inspected_at")
    private OffsetDateTime inspectedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
