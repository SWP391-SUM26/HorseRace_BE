package com.SWP391.horserace.referee.service.impl;

import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what is left of {@code RefereeService} after the deprecated report workflow was removed:
 * the pre-race horse health check.
 */
@ExtendWith(MockitoExtension.class)
class RefereeServiceImplTest {

    @Mock HorseRepository horseRepository;

    private RefereeServiceImpl service;

    private final UUID callerId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RefereeServiceImpl(horseRepository);
    }

    @Test
    void healthCheck_horseNotFound_throws() {
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordHealthCheck(callerId, horseId,
                new HealthCheckRequest(HorseHealthStatus.HEALTHY, "ok")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.HORSE_NOT_FOUND.getMessage());

        verify(horseRepository, never()).save(any());
    }

    @Test
    void healthCheck_happyPath_setsStatusAndLastCheck() {
        Horse horse = Horse.builder().horseId(horseId).name("Bạch Long Mã")
                .healthStatus(HorseHealthStatus.QUARANTINE).build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordHealthCheck(callerId, horseId,
                new HealthCheckRequest(HorseHealthStatus.HEALTHY, "Đã hồi phục"));

        assertThat(horse.getHealthStatus()).isEqualTo(HorseHealthStatus.HEALTHY);
        assertThat(horse.getLastHealthCheckAt()).isNotNull();
        assertThat(horse.getMedicalNote()).isEqualTo("Đã hồi phục");
    }

    @Test
    void healthCheck_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.recordHealthCheck(null, horseId,
                new HealthCheckRequest(HorseHealthStatus.HEALTHY, null)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.UNAUTHENTICATED.getMessage());

        verify(horseRepository, never()).save(any());
    }
}
