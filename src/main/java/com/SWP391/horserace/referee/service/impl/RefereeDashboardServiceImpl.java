package com.SWP391.horserace.referee.service.impl;

import com.SWP391.horserace.assignments.entity.RefereeAssignment;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.inspections.entity.InspectionStatus;
import com.SWP391.horserace.inspections.entity.RaceEntryInspection;
import com.SWP391.horserace.inspections.repository.RaceEntryInspectionRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.referee.dto.RefereeDashboardResponse;
import com.SWP391.horserace.referee.service.RefereeDashboardService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.violations.entity.RaceViolation;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import com.SWP391.horserace.violations.repository.RaceViolationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefereeDashboardServiceImpl implements RefereeDashboardService {

    private static final List<RaceStatus> UPCOMING_STATUSES = List.of(RaceStatus.SCHEDULED, RaceStatus.OPEN);

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RaceEntryInspectionRepository inspectionRepository;
    private final RaceViolationRepository violationRepository;
    private final RefereeAssignmentRepository refereeAssignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public RefereeDashboardResponse getDashboard(UUID userId, UUID raceId) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = resolveNextRace(raceId);

        if (race == null) {
            return RefereeDashboardResponse.builder()
                    .nextRace(null)
                    .alerts(List.of())
                    .inspectionSummary(RefereeDashboardResponse.InspectionSummary.builder()
                            .cleared(0).pending(0).vetCheck(0).total(0).build())
                    .dutyRoster(List.of())
                    .build();
        }

        return RefereeDashboardResponse.builder()
                .nextRace(toNextRace(race))
                .alerts(buildAlerts(race))
                .inspectionSummary(buildInspectionSummary(race))
                .dutyRoster(buildDutyRoster(race))
                .build();
    }

    /** When a raceId is given use that race (404 if missing); otherwise the soonest upcoming one. */
    private Race resolveNextRace(UUID raceId) {
        if (raceId != null) {
            return raceRepository.findByRaceIdAndDeletedFalse(raceId)
                    .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));
        }
        List<Race> upcoming = raceRepository.findUpcomingByStatuses(UPCOMING_STATUSES, PageRequest.of(0, 1));
        return upcoming.isEmpty() ? null : upcoming.get(0);
    }

    private RefereeDashboardResponse.NextRace toNextRace(Race race) {
        OffsetDateTime start = race.getScheduledStartAt();
        Long countdown = null;
        if (start != null) {
            long secs = Duration.between(OffsetDateTime.now(), start).getSeconds();
            countdown = Math.max(0L, secs);
        }
        return RefereeDashboardResponse.NextRace.builder()
                .raceId(race.getRaceId())
                .raceCode(race.getRaceCode())
                .name(race.getName())
                .scheduledStartAt(start)
                .postTimeCountdownSeconds(countdown)
                .status(race.getStatus())
                .build();
    }

    private List<RefereeDashboardResponse.Alert> buildAlerts(Race race) {
        UUID raceId = race.getRaceId();
        List<RefereeDashboardResponse.Alert> alerts = new ArrayList<>();

        // Pending violations → "<INFRACTION>_PENDING" alerts.
        List<RaceViolation> pending =
                violationRepository.findByRaceIdAndStatusWithDetails(raceId, ViolationStatus.PENDING);
        for (RaceViolation v : pending) {
            String infraction = v.getInfractionType() != null ? v.getInfractionType().name() : "VIOLATION";
            String horseName = horseNameOf(v.getEntry());
            String displayLabel = (horseName != null ? infraction + " pending — " + horseName
                    : infraction + " pending — Race " + race.getRaceCode());
            alerts.add(RefereeDashboardResponse.Alert.builder()
                    .type(infraction + "_PENDING")
                    .severity(v.getSeverity() != null ? v.getSeverity().name() : null)
                    .raceId(raceId)
                    .entryId(v.getEntry() != null ? v.getEntry().getEntryId() : null)
                    .refId(v.getViolationId())
                    .label(displayLabel)
                    .build());
        }

        // VET_CHECK inspections → "VET_CHECK_REQUESTED" alerts.
        List<RaceEntry> entries = raceEntryRepository.findByRaceIdWithHorse(raceId);
        if (!entries.isEmpty()) {
            List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
            Map<UUID, RaceEntryInspection> byEntry = inspectionRepository.findByEntry_EntryIdIn(entryIds)
                    .stream().collect(Collectors.toMap(i -> i.getEntry().getEntryId(), i -> i));
            for (RaceEntry entry : entries) {
                RaceEntryInspection insp = byEntry.get(entry.getEntryId());
                if (insp != null && insp.getInspectionStatus() == InspectionStatus.VET_CHECK) {
                    String horseName = horseNameOf(entry);
                    alerts.add(RefereeDashboardResponse.Alert.builder()
                            .type("VET_CHECK_REQUESTED")
                            .severity("MEDIUM")
                            .raceId(raceId)
                            .entryId(entry.getEntryId())
                            .refId(insp.getInspectionId())
                            .label("Vet check requested — " + (horseName != null ? horseName : "Unknown"))
                            .build());
                }
            }
        }
        return alerts;
    }

    private RefereeDashboardResponse.InspectionSummary buildInspectionSummary(Race race) {
        List<RaceEntry> entries = raceEntryRepository.findByRaceIdWithHorse(race.getRaceId());
        long total = entries.size();
        if (total == 0) {
            return RefereeDashboardResponse.InspectionSummary.builder()
                    .cleared(0).pending(0).vetCheck(0).total(0).build();
        }
        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
        Map<UUID, RaceEntryInspection> byEntry = inspectionRepository.findByEntry_EntryIdIn(entryIds)
                .stream().collect(Collectors.toMap(i -> i.getEntry().getEntryId(), i -> i));

        long cleared = 0, pending = 0, vetCheck = 0;
        for (RaceEntry entry : entries) {
            RaceEntryInspection insp = byEntry.get(entry.getEntryId());
            // Entries with no inspection count as pending.
            InspectionStatus s = insp != null ? insp.getInspectionStatus() : InspectionStatus.PENDING;
            switch (s) {
                case CLEARED -> cleared++;
                case VET_CHECK -> vetCheck++;
                default -> pending++;
            }
        }
        return RefereeDashboardResponse.InspectionSummary.builder()
                .cleared(cleared).pending(pending).vetCheck(vetCheck).total(total).build();
    }

    private List<RefereeDashboardResponse.DutyRosterItem> buildDutyRoster(Race race) {
        List<RefereeAssignment> assignments = refereeAssignmentRepository.findByRace_RaceId(race.getRaceId());
        return assignments.stream()
                .map(a -> RefereeDashboardResponse.DutyRosterItem.builder()
                        .refereeUserId(a.getReferee() != null ? a.getReferee().getUserId() : null)
                        .refereeName(a.getReferee() != null ? a.getReferee().getFullName() : null)
                        .panelRole(a.getPanelRole() != null ? a.getPanelRole().name() : null)
                        .station(null) // no station column in referee_assignment
                        .build())
                .toList();
    }

    private String horseNameOf(RaceEntry entry) {
        if (entry == null || entry.getRegistration() == null) {
            return null;
        }
        Horse horse = entry.getRegistration().getHorse();
        return horse != null ? horse.getName() : null;
    }
}
