package com.SWP391.horserace.jockeys.entity;

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
import java.util.UUID;

/**
 * Maps the {@code jockey_profile} table (professional profile of a JOCKEY user).
 * The primary key IS the {@code app_user(user_id)} of the jockey (shared/derived id),
 * so a profile is a 1:1 extension of an existing user. The app layer ensures the user's
 * role is JOCKEY.
 */
@Entity
@Table(name = "jockey_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyProfile {

    /** Same value as the owning {@link User}'s id (derived via {@link MapsId}). */
    @Id
    @Column(name = "jockey_user_id", updatable = false, nullable = false)
    private UUID jockeyUserId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jockey_user_id")
    private User jockeyUser;

    @Column(name = "license_no", unique = true, length = 100)
    private String licenseNo;

    /** kg, used for handicap. */
    @Column(name = "body_weight", precision = 5, scale = 2)
    private BigDecimal bodyWeight;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "experience_yrs")
    private Integer experienceYrs;

    /** Cached win tally, updated when results are finalized. */
    @Column(name = "win_count", nullable = false)
    @Builder.Default
    private Integer winCount = 0;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
