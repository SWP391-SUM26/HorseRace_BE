package com.SWP391.horserace.horses.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code horse_medical_record} table — owner-managed medical records per horse. */
@Entity
@Table(name = "horse_medical_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseMedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "record_id", updatable = false, nullable = false)
    private UUID recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id")
    private Horse horse;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    @Builder.Default
    private MedicalRecordType recordType = MedicalRecordType.NOTE;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
