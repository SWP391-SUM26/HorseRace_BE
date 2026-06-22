package com.SWP391.horserace.horses.entity;

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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code horse} table. */
@Entity
@Table(name = "horse")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "horse_id", updatable = false, nullable = false)
    private UUID horseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @Column(name = "horse_code", nullable = false, unique = true, length = 50)
    private String horseCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "microchip_no", unique = true, length = 100)
    private String microchipNo;

    /** MALE | FEMALE | GELDING */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private HorseGender gender;

    @Column(name = "breed", length = 100)
    private String breed;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "weight", precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    /** HEALTHY | INJURED | QUARANTINE | UNFIT */
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 50)
    private HorseHealthStatus healthStatus;

    @Column(name = "registration_status", length = 50)
    private String registrationStatus;

    /** ACTIVE | RETIRED | INACTIVE */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private HorseStatus status = HorseStatus.ACTIVE;

    /** Public URL of the horse photo, served via GET /api/v1/files/{key}. */
    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "last_health_check_at")
    private OffsetDateTime lastHealthCheckAt;

    @Column(name = "medical_note", columnDefinition = "text")
    private String medicalNote;

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
