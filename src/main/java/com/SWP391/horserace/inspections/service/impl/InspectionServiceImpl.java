package com.SWP391.horserace.inspections.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.inspections.dto.InspectionListItemResponse;
import com.SWP391.horserace.inspections.dto.InspectionRequest;
import com.SWP391.horserace.inspections.dto.InspectionResponse;
import com.SWP391.horserace.inspections.dto.SubmitAllRequest;
import com.SWP391.horserace.inspections.dto.SubmitAllResponse;
import com.SWP391.horserace.inspections.entity.InspectionStatus;
import com.SWP391.horserace.inspections.entity.RaceEntryInspection;
import com.SWP391.horserace.inspections.repository.RaceEntryInspectionRepository;
import com.SWP391.horserace.inspections.service.InspectionService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionServiceImpl implements InspectionService {

    private final RaceEntryInspectionRepository inspectionRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public InspectionResponse upsertInspection(UUID currentUserId, UUID raceId, InspectionRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        RaceEntry entry = raceEntryRepository.findByIdWithDetails(request.entryId())
                .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));

        // Entry must belong to the race named in the path.
        if (entry.getRace() == null || !raceId.equals(entry.getRace().getRaceId())) {
            throw new AppException(ErrorCode.INSPECTION_ENTRY_RACE_MISMATCH);
        }

        User inspector = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // One inspection per entry — upsert.
        RaceEntryInspection inspection = inspectionRepository.findByEntry_EntryId(request.entryId())
                .orElseGet(() -> RaceEntryInspection.builder()
                        .entry(entry)
                        .race(race)
                        .build());

        inspection.setRace(race);
        inspection.setHealthCertPassed(Boolean.TRUE.equals(request.healthCertPassed()));
        inspection.setWeightVerified(Boolean.TRUE.equals(request.weightVerified()));
        inspection.setWeightCarriedLbs(request.weightCarriedLbs());
        inspection.setCogginsTestPassed(Boolean.TRUE.equals(request.cogginsTestPassed()));
        inspection.setPreRaceExamPassed(Boolean.TRUE.equals(request.preRaceExamPassed()));
        inspection.setInspectionStatus(
                request.inspectionStatus() != null ? request.inspectionStatus() : InspectionStatus.PENDING);
        inspection.setStewardNote(request.stewardNote());
        inspection.setInspectedBy(inspector);
        inspection.setInspectedAt(OffsetDateTime.now());

        RaceEntryInspection saved = inspectionRepository.save(inspection);
        return toResponse(saved, entry, inspector);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionListItemResponse> listInspections(UUID raceId, InspectionStatus status) {
        if (!raceRepository.existsById(raceId)) {
            throw new AppException(ErrorCode.RACE_NOT_FOUND);
        }

        List<RaceEntry> entries = raceEntryRepository.findByRaceIdWithHorse(raceId);
        if (entries.isEmpty()) {
            return List.of();
        }

        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();

        // Inspections that exist for these entries, keyed by entryId.
        Map<UUID, RaceEntryInspection> inspectionsByEntry = inspectionRepository.findByEntry_EntryIdIn(entryIds)
                .stream()
                .collect(Collectors.toMap(i -> i.getEntry().getEntryId(), i -> i));

        // Riding (ACCEPTED) jockey name per entry, resolved in one query.
        Map<UUID, String> jockeyNameByEntry = new HashMap<>();
        for (JockeyAssignment ja : jockeyAssignmentRepository.findAcceptedByEntryIds(entryIds)) {
            jockeyNameByEntry.put(ja.getEntry().getEntryId(),
                    ja.getJockey() != null ? ja.getJockey().getFullName() : null);
        }

        return entries.stream()
                .map(entry -> {
                    RaceEntryInspection insp = inspectionsByEntry.get(entry.getEntryId());
                    Horse horse = entry.getRegistration() != null ? entry.getRegistration().getHorse() : null;

                    InspectionStatus itemStatus = insp != null ? insp.getInspectionStatus() : InspectionStatus.PENDING;
                    return InspectionListItemResponse.builder()
                            .inspectionId(insp != null ? insp.getInspectionId() : null)
                            .entryId(entry.getEntryId())
                            .laneNo(entry.getLaneNo())
                            .horseId(horse != null ? horse.getHorseId() : null)
                            .horseName(horse != null ? horse.getName() : null)
                            .jockeyName(jockeyNameByEntry.get(entry.getEntryId()))
                            .healthCertPassed(insp != null && insp.isHealthCertPassed())
                            .weightVerified(insp != null && insp.isWeightVerified())
                            .inspectionStatus(itemStatus)
                            .inspectedAt(insp != null ? insp.getInspectedAt() : null)
                            .build();
                })
                .filter(item -> status == null || item.getInspectionStatus() == status)
                .toList();
    }

    @Override
    @Transactional
    public SubmitAllResponse submitAll(UUID raceId, SubmitAllRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new AppException(ErrorCode.INSPECTION_CONFIRM_REQUIRED);
        }
        if (!raceRepository.existsById(raceId)) {
            throw new AppException(ErrorCode.RACE_NOT_FOUND);
        }

        List<RaceEntry> entries = raceEntryRepository.findByRaceIdWithHorse(raceId);
        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();

        Map<UUID, RaceEntryInspection> inspectionsByEntry = entryIds.isEmpty()
                ? Map.of()
                : inspectionRepository.findByEntry_EntryIdIn(entryIds).stream()
                        .collect(Collectors.toMap(i -> i.getEntry().getEntryId(), i -> i));

        long submittedCount = 0;
        List<SubmitAllResponse.BlockedEntry> blocked = new java.util.ArrayList<>();

        for (RaceEntry entry : entries) {
            RaceEntryInspection insp = inspectionsByEntry.get(entry.getEntryId());
            InspectionStatus s = insp != null ? insp.getInspectionStatus() : InspectionStatus.PENDING;
            if (s == InspectionStatus.CLEARED) {
                submittedCount++;
            } else {
                Horse horse = entry.getRegistration() != null ? entry.getRegistration().getHorse() : null;
                blocked.add(SubmitAllResponse.BlockedEntry.builder()
                        .entryId(entry.getEntryId())
                        .horseName(horse != null ? horse.getName() : null)
                        .reason(insp == null ? "Not inspected" : s + " unresolved")
                        .build());
            }
        }

        return SubmitAllResponse.builder()
                .raceId(raceId)
                .submittedCount(submittedCount)
                .blockedEntries(blocked)
                .build();
    }

    private InspectionResponse toResponse(RaceEntryInspection i, RaceEntry entry, User inspector) {
        Horse horse = entry.getRegistration() != null ? entry.getRegistration().getHorse() : null;
        return InspectionResponse.builder()
                .inspectionId(i.getInspectionId())
                .entryId(entry.getEntryId())
                .raceId(i.getRace() != null ? i.getRace().getRaceId() : null)
                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getName() : null)
                .laneNo(entry.getLaneNo())
                .healthCertPassed(i.isHealthCertPassed())
                .weightVerified(i.isWeightVerified())
                .weightCarriedLbs(i.getWeightCarriedLbs())
                .cogginsTestPassed(i.isCogginsTestPassed())
                .preRaceExamPassed(i.isPreRaceExamPassed())
                .inspectionStatus(i.getInspectionStatus())
                .stewardNote(i.getStewardNote())
                .inspectedByUserId(inspector != null ? inspector.getUserId() : null)
                .inspectedByName(inspector != null ? inspector.getFullName() : null)
                .inspectedAt(i.getInspectedAt())
                .build();
    }
}
