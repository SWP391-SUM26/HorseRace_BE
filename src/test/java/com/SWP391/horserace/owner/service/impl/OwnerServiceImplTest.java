package com.SWP391.horserace.owner.service.impl;

import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.owner.dto.OwnerOverviewResponse;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerServiceImplTest {

    @Mock HorseRepository horseRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock RaceResultRepository raceResultRepository;

    private OwnerServiceImpl service;

    private final UUID ownerId = UUID.randomUUID();
    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder().userId(ownerId).fullName("Owen Owner").build();
        service = new OwnerServiceImpl(horseRepository, raceEntryRepository, raceResultRepository);
    }

    private Horse horse(String code, String name, HorseStatus status, BigDecimal earnings) {
        return Horse.builder()
                .horseId(UUID.randomUUID())
                .owner(owner)
                .horseCode(code)
                .name(name)
                .status(status)
                .lifetimeEarnings(earnings)
                .build();
    }

    private RaceEntry entryWithResult(Integer finishPosition, List<RaceResult> sink) {
        RaceEntry entry = RaceEntry.builder()
                .entryId(UUID.randomUUID())
                .entryCode("ENT" + UUID.randomUUID())
                .status(RaceEntryStatus.ENTERED)
                .build();
        if (finishPosition != null) {
            sink.add(RaceResult.builder()
                    .resultId(UUID.randomUUID())
                    .entry(entry)
                    .finishPosition(finishPosition)
                    .build());
        }
        return entry;
    }

    // ── getOwnerHorses ──

    @Test
    void getOwnerHorses_returnsOnlyCallersHorses() {
        Horse a = horse("HRS0001", "Midnight", HorseStatus.ACTIVE, new BigDecimal("100"));
        Horse b = horse("HRS0002", "Storm", HorseStatus.RETIRED, new BigDecimal("50"));
        when(horseRepository.findByOwner_UserIdAndDeletedFalse(ownerId)).thenReturn(List.of(a, b));

        List<HorseResponse> result = service.getOwnerHorses(ownerId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(HorseResponse::getHorseCode)
                .containsExactly("HRS0001", "HRS0002");
        assertThat(result).allSatisfy(r -> assertThat(r.getOwnerUserId()).isEqualTo(ownerId));
    }

    @Test
    void getOwnerHorses_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.getOwnerHorses(null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    // ── getOverview ──

    @Test
    void getOverview_mapsKpisHorsesAndUpcomingRaces() {
        Horse active1 = horse("HRS0001", "Midnight", HorseStatus.ACTIVE, new BigDecimal("612000"));
        Horse active2 = horse("HRS0002", "Thunder", HorseStatus.ACTIVE, new BigDecimal("230500"));
        Horse retired = horse("HRS0003", "OldBoy", HorseStatus.RETIRED, new BigDecimal("100000"));
        when(horseRepository.findByOwner_UserIdAndDeletedFalse(ownerId))
                .thenReturn(List.of(active1, active2, retired));

        // 4 entries: positions 1, 2, 4 (with result), and one with no result row.
        java.util.List<RaceResult> results = new java.util.ArrayList<>();
        RaceEntry e1 = entryWithResult(1, results);
        RaceEntry e2 = entryWithResult(2, results);
        RaceEntry e3 = entryWithResult(4, results);
        RaceEntry e4 = entryWithResult(null, results);
        when(raceEntryRepository.findByOwnerUserId(ownerId)).thenReturn(List.of(e1, e2, e3, e4));
        when(raceResultRepository.findByEntry_EntryIdIn(anyCollection())).thenReturn(results);

        // one upcoming race
        Race race = Race.builder()
                .raceId(UUID.randomUUID())
                .name("The Gold Cup")
                .venue("Royal Ascot")
                .scheduledStartAt(OffsetDateTime.parse("2026-06-20T13:00:00Z"))
                .build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(UUID.randomUUID())
                .owner(owner)
                .horse(active1)
                .build();
        RaceEntry upcoming = RaceEntry.builder()
                .entryId(UUID.randomUUID())
                .entryCode("ENT99999")
                .registration(reg)
                .race(race)
                .status(RaceEntryStatus.ENTERED)
                .build();
        when(raceEntryRepository.findUpcomingByOwnerUserId(ownerId)).thenReturn(List.of(upcoming));

        OwnerOverviewResponse res = service.getOverview(ownerId);

        // KPIs computed from real data
        OwnerOverviewResponse.Kpis kpis = res.getKpis();
        assertThat(kpis.getLifetimeEarnings()).isEqualByComparingTo("942500"); // 612000+230500+100000
        assertThat(kpis.getActiveHorses()).isEqualTo(2);
        assertThat(kpis.getStarts()).isEqualTo(3); // three results with a finish position
        assertThat(kpis.getWins()).isEqualTo(1);   // position == 1
        assertThat(kpis.getTop3()).isEqualTo(2);   // positions 1 and 2

        // finance KPIs are placeholders (§5 not built)
        assertThat(kpis.getNetProfit()).isEqualByComparingTo("0");
        assertThat(kpis.getMargin()).isNull();
        assertThat(kpis.getNetProfitTrend()).isNull();
        assertThat(kpis.getPendingPayouts()).isEqualByComparingTo("0");
        assertThat(kpis.getPendingCount()).isZero();
        assertThat(kpis.getPendingEtaDays()).isNull();

        // horses[]
        assertThat(res.getHorses()).hasSize(3);
        OwnerOverviewResponse.OverviewHorse h0 = res.getHorses().get(0);
        assertThat(h0.getRegistrationCode()).isEqualTo("HRS0001");
        assertThat(h0.getName()).isEqualTo("Midnight");
        assertThat(h0.getStatus()).isEqualTo("ACTIVE");
        assertThat(h0.getEarnings()).isEqualByComparingTo("612000");

        // upcomingRaces[]
        assertThat(res.getUpcomingRaces()).hasSize(1);
        OwnerOverviewResponse.UpcomingRace u = res.getUpcomingRaces().get(0);
        assertThat(u.getName()).isEqualTo("The Gold Cup");
        assertThat(u.getVenue()).isEqualTo("Royal Ascot");
        assertThat(u.getYourHorse()).isEqualTo("Midnight");
        assertThat(u.getEntryStatus()).isEqualTo("ENTERED");
    }

    @Test
    void getOverview_noEntries_statsAreZero() {
        when(horseRepository.findByOwner_UserIdAndDeletedFalse(ownerId)).thenReturn(List.of());
        when(raceEntryRepository.findByOwnerUserId(ownerId)).thenReturn(List.of());
        when(raceEntryRepository.findUpcomingByOwnerUserId(ownerId)).thenReturn(List.of());

        OwnerOverviewResponse res = service.getOverview(ownerId);

        assertThat(res.getKpis().getStarts()).isZero();
        assertThat(res.getKpis().getWins()).isZero();
        assertThat(res.getKpis().getTop3()).isZero();
        assertThat(res.getKpis().getActiveHorses()).isZero();
        assertThat(res.getKpis().getLifetimeEarnings()).isEqualByComparingTo("0");
        assertThat(res.getHorses()).isEmpty();
        assertThat(res.getUpcomingRaces()).isEmpty();
    }

    @Test
    void getOverview_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.getOverview(null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }
}
