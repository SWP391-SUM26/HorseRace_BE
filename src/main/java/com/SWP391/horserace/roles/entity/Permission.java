package com.SWP391.horserace.roles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code permission} table (fine-grained permission assignable to roles). */
@Entity
@Table(name = "permission")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "permission_id", updatable = false, nullable = false)
    private UUID permissionId;

    /** e.g. TOURNAMENT_MANAGE, RESULT_PUBLISH, PREDICTION_PLACE. */
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}
