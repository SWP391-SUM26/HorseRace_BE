package com.SWP391.horserace.referee.service.impl;

import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;
import com.SWP391.horserace.referee.service.RefereeService;
import com.SWP391.horserace.reports.entity.ReportStatus;
import com.SWP391.horserace.reports.entity.ReportType;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefereeServiceImpl implements RefereeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final HorseRepository horseRepository;

    // ── horse health check ──

    @Override
    @Transactional
    public MedicalStatusResponse recordHealthCheck(UUID currentUserId, UUID horseId, HealthCheckRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Horse horse = horseRepository.findByHorseIdAndDeletedFalse(horseId)
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));

        horse.setHealthStatus(request.healthStatus());
        horse.setLastHealthCheckAt(OffsetDateTime.now());
        if (request.note() != null) {
            horse.setMedicalNote(request.note());
        }

        return mapToMedicalStatus(horseRepository.save(horse));
    }

    private MedicalStatusResponse mapToMedicalStatus(Horse h) {
        return MedicalStatusResponse.builder()
                .horseId(h.getHorseId())
                .horseName(h.getName())
                .healthStatus(h.getHealthStatus())
                .lastHealthCheckAt(h.getLastHealthCheckAt())
                .medicalNote(h.getMedicalNote())
                .build();
    }
}
