package com.SWP391.horserace.owner.entity;

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

/**
 * Maps the {@code owner_profile} table (extended profile of a HORSE_OWNER user).
 * The primary key IS the {@code app_user(user_id)} of the owner (shared/derived id via
 * {@link MapsId}), so a profile is a 1:1 extension of an existing user. The app layer ensures
 * the user's role is HORSE_OWNER.
 */
@Entity
@Table(name = "owner_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerProfile {

    /** Same value as the owning {@link User}'s id (derived via {@link MapsId}). */
    @Id
    @Column(name = "owner_user_id", updatable = false, nullable = false)
    private UUID ownerUserId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @Column(name = "stable_name", length = 100)
    private String stableName;

    /** Region code as sent by the FE (e.g. "KY-US", "VN"). */
    @Column(name = "primary_region", length = 100)
    private String primaryRegion;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
