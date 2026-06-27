package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.races.dto.GlobalResultResponse;
import com.SWP391.horserace.races.dto.ResultFilterRequest;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.races.repository.RaceResultSpecification;
import com.SWP391.horserace.races.service.ResultManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResultManagementServiceImpl implements ResultManagementService {

    private final RaceResultRepository raceResultRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<GlobalResultResponse> getGlobalResults(ResultFilterRequest filter, Pageable pageable) {
        Specification<RaceResult> spec = RaceResultSpecification.filter(filter);
        Page<RaceResult> resultPage = raceResultRepository.findAll(spec, pageable);

        List<UUID> entryIds = resultPage.getContent().stream()
                .filter(r -> r.getEntry() != null)
                .map(r -> r.getEntry().getEntryId())
                .toList();

        Map<UUID, UserInfo> jockeyInfoByEntry = getJockeyInfoByEntryIds(entryIds);

        return resultPage.map(rr -> mapToGlobalResponse(rr, jockeyInfoByEntry));
    }

    private Map<UUID, UserInfo> getJockeyInfoByEntryIds(List<UUID> entryIds) {
        if (entryIds.isEmpty())
            return Map.of();
        Map<UUID, UserInfo> byEntry = new HashMap<>();
        for (JockeyAssignment ja : jockeyAssignmentRepository.findAcceptedByEntryIds(entryIds)) {
            if (ja.getEntry() != null && ja.getJockey() != null) {
                byEntry.put(ja.getEntry().getEntryId(),
                        new UserInfo(ja.getJockey().getUserId(), ja.getJockey().getFullName()));
            }
        }
        return byEntry;
    }

    private record UserInfo(UUID id, String name) {
    }

    private GlobalResultResponse mapToGlobalResponse(RaceResult rr, Map<UUID, UserInfo> jockeyInfoByEntry) {
        Race race = rr.getRace();
        RaceEntry entry = rr.getEntry();

        var horse = entry != null && entry.getRegistration() != null
                ? entry.getRegistration().getHorse()
                : null;

        UserInfo jockeyInfo = entry != null ? jockeyInfoByEntry.get(entry.getEntryId()) : null;

        return GlobalResultResponse.builder()
                .resultId(rr.getResultId())
                .raceId(race != null ? race.getRaceId() : null)
                .raceName(race != null ? race.getName() : null)
                .raceCode(race != null ? race.getRaceCode() : null)
                .raceScheduledStartAt(race != null ? race.getScheduledStartAt() : null)

                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getName() : null)

                .jockeyId(jockeyInfo != null ? jockeyInfo.id() : null)
                .jockeyName(jockeyInfo != null ? jockeyInfo.name() : null)

                .finishPosition(rr.getFinishPosition())
                .finishTimeMs(rr.getFinishTimeMs()) // This can safely be null for DNF/Scratched
                .lengthsBehind(rr.getLengthsBehind())
                .score(rr.getScore())
                .officialityStatus(rr.getOfficialityStatus())
                .build();
    }
}
