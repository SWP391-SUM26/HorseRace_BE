package com.SWP391.horserace.referee.service;

import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;

import java.util.UUID;

/**
 * Referee-side horse checks.
 *
 * <p>The report workflow that used to live here (create/list/update/submit) was superseded by race
 * violations and was already flagged deprecated in code — {@code submitReport} did nothing but
 * throw. Removed along with its DTOs, repository and error code.
 */
public interface RefereeService {

    MedicalStatusResponse recordHealthCheck(UUID currentUserId, UUID horseId, HealthCheckRequest request);
}
