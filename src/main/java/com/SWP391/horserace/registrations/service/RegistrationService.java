package com.SWP391.horserace.registrations.service;

import com.SWP391.horserace.registrations.dto.RegistrationFilterRequest;
import com.SWP391.horserace.registrations.dto.RegistrationRequest;
import com.SWP391.horserace.registrations.dto.RegistrationResponse;
import com.SWP391.horserace.registrations.dto.RegistrationStatsResponse;
import com.SWP391.horserace.registrations.dto.RejectRegistrationRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface RegistrationService {

    RegistrationResponse submitRegistration(UUID currentUserId, RegistrationRequest request);

    Page<RegistrationResponse> listRegistrations(RegistrationFilterRequest filter);

    RegistrationResponse getRegistrationById(UUID id);

    /** KPI aggregate (total / pending / approved / rejected), optionally scoped to a tournament. */
    RegistrationStatsResponse getStats(UUID tournamentId);

    RegistrationResponse approveRegistration(UUID currentUserId, UUID id);

    RegistrationResponse rejectRegistration(UUID currentUserId, UUID id, RejectRegistrationRequest request);

    RegistrationResponse withdrawRegistration(UUID currentUserId, UUID id);
}
