package com.SWP391.horserace.prizes.entity;

import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.tournaments.entity.Tournament;
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

/** Maps the {@code prize} table. */
@Entity
@Table(name = "prize")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prize {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "prize_id", updatable = false, nullable = false)
    private UUID prizeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @Column(name = "prize_code", nullable = false, unique = true, length = 50)
    private String prizeCode;

    /** OWNER | JOCKEY | HORSE | TEAM */
    @Column(name = "beneficiary_type", length = 50)
    private String beneficiaryType;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "prize_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal prizeAmount;

    @Column(name = "currency_code", nullable = false, length = 10)
    @Builder.Default
    private String currencyCode = "VND";

    /** DRAFT | ANNOUNCED | AWARDED | PAID | CANCELLED */
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "DRAFT";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
