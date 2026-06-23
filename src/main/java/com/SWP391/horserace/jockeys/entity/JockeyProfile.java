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

    // -- Jockey Market (FE-v2 §2) marketing/stat fields --

    /** Star rating, 0–5 (e.g. 4.9). */
    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    /** Riding style label (e.g. Stalker, Closer, Front-runner). */
    @Column(name = "riding_style", length = 50)
    private String ridingStyle;

    /** Win rate as a percentage, 0–100 (e.g. 62.50). */
    @Column(name = "win_rate", precision = 5, scale = 2)
    private BigDecimal winRate;

    /** Recent form stored comma-joined, e.g. {@code "W,L,W,W,L"}; exposed as a list. */
    @Column(name = "recent_form", length = 50)
    private String recentForm;

    /** Base hire fee. */
    @Column(name = "base_fee", precision = 18, scale = 2)
    private BigDecimal baseFee;

    /** Percentage of prize taken by the jockey. */
    @Column(name = "prize_percent", precision = 5, scale = 2)
    private BigDecimal prizePercent;

    /** Name of the most recent trophy won. */
    @Column(name = "last_trophy", length = 255)
    private String lastTrophy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
