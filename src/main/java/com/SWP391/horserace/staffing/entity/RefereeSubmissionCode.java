package com.SWP391.horserace.staffing.entity;

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

/**
 * Maps the {@code referee_submission_code} table (CN3). A 6-digit OTP emailed to a referee's
 * verified email before they publish a race report. Only a SHA-256 hash of the code is stored
 * (never the plaintext), single-use ({@code consumedAt}), attempt-limited ({@code attemptCount}),
 * and bound to {@code (race, referee)}. Mirrors {@code EmailVerificationToken}.
 */
@Entity
@Table(name = "referee_submission_code")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeSubmissionCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "code_id", updatable = false, nullable = false)
    private UUID codeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_user_id", nullable = false)
    private User referee;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Transient
    public boolean isExpired() {
        return expiresAt.isBefore(OffsetDateTime.now());
    }
}
