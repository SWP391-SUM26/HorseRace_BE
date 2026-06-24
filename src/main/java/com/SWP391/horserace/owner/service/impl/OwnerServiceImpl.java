package com.SWP391.horserace.owner.service.impl;

import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.owner.dto.OwnerOverviewResponse;
import com.SWP391.horserace.owner.service.OwnerService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerServiceImpl implements OwnerService {

    private final HorseRepository horseRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RaceResultRepository raceResultRepository;

    @Override
    @Transactional(readOnly = true)
    public List<HorseResponse> getOwnerHorses(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return horseRepository.findByOwner_UserIdAndDeletedFalse(ownerUserId).stream()
                .map(OwnerServiceImpl::mapToHorseResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerOverviewResponse getOverview(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        List<Horse> horses = horseRepository.findByOwner_UserIdAndDeletedFalse(ownerUserId);

        // lifetimeEarnings = SUM of the owner's horses' lifetimeEarnings.
        BigDecimal lifetimeEarnings = horses.stream()
                .map(Horse::getLifetimeEarnings)
                .filter(e -> e != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeHorses = horses.stream()
                .filter(h -> h.getStatus() == HorseStatus.ACTIVE)
                .count();

        // starts/wins/top3 from race_result over the owner's entries — mirrors HorseServiceImpl.getStats.
        List<RaceEntry> entries = raceEntryRepository.findByOwnerUserId(ownerUserId);
        long starts = 0;
        long wins = 0;
        long top3 = 0;
        if (!entries.isEmpty()) {
            List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
            List<RaceResult> results = raceResultRepository.findByEntry_EntryIdIn(entryIds).stream()
                    .filter(r -> r.getFinishPosition() != null)
                    .toList();
            starts = results.size();
            for (RaceResult r : results) {
                int pos = r.getFinishPosition();
                if (pos == 1) wins++;
                if (pos <= 3) top3++;
            }
        }

        OwnerOverviewResponse.Kpis kpis = OwnerOverviewResponse.Kpis.builder()
                .lifetimeEarnings(lifetimeEarnings)
                .starts(starts)
                .wins(wins)
                .top3(top3)
                .activeHorses(activeHorses)
                // TODO(finance §5): wire real values when Finance module lands.
                .netProfit(BigDecimal.ZERO)
                .margin(null)
                .netProfitTrend(null)
                .pendingPayouts(BigDecimal.ZERO)
                .pendingCount(0)
                .pendingEtaDays(null)
                .build();

        List<OwnerOverviewResponse.OverviewHorse> overviewHorses = horses.stream()
                .map(h -> OwnerOverviewResponse.OverviewHorse.builder()
                        .horseId(h.getHorseId())
                        .registrationCode(h.getHorseCode())
                        .name(h.getName())
                        .status(h.getStatus() != null ? h.getStatus().name() : null)
                        .earnings(h.getLifetimeEarnings())
                        .build())
                .toList();

        List<OwnerOverviewResponse.UpcomingRace> upcomingRaces =
                raceEntryRepository.findUpcomingByOwnerUserId(ownerUserId).stream()
                        .map(OwnerServiceImpl::mapToUpcomingRace)
                        .toList();

        return OwnerOverviewResponse.builder()
                .kpis(kpis)
                .horses(overviewHorses)
                .upcomingRaces(upcomingRaces)
                .build();
    }

    // ── helpers ──

    private static OwnerOverviewResponse.UpcomingRace mapToUpcomingRace(RaceEntry e) {
        Race race = e.getRace();
        TournamentRegistration reg = e.getRegistration();
        Horse horse = reg != null ? reg.getHorse() : null;
        return OwnerOverviewResponse.UpcomingRace.builder()
                .raceId(race != null ? race.getRaceId() : null)
                .name(race != null ? race.getName() : null)
                .venue(race != null ? race.getVenue() : null)
                .date(race != null ? race.getScheduledStartAt() : null)
                .yourHorse(horse != null ? horse.getName() : null)
                .entryStatus(e.getStatus() != null ? e.getStatus().name() : null)
                .build();
    }

    /** Mirrors HorseServiceImpl.mapToResponse so /owner/horses returns the same HorseResponse shape. */
    private static HorseResponse mapToHorseResponse(Horse h) {
        User owner = h.getOwner();
        return HorseResponse.builder()
                .horseId(h.getHorseId())
                .horseCode(h.getHorseCode())
                .ownerUserId(owner != null ? owner.getUserId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .name(h.getName())
                .microchipNo(h.getMicrochipNo())
                .gender(h.getGender())
                .breed(h.getBreed())
                .color(h.getColor())
                .dateOfBirth(h.getDateOfBirth())
                .weight(h.getWeight())
                .originCountry(h.getOriginCountry())
                .healthStatus(h.getHealthStatus())
                .registrationStatus(h.getRegistrationStatus())
                .status(h.getStatus())
                .imageUrl(h.getImageUrl())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
