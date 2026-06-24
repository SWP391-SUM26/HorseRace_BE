package com.SWP391.horserace.jockeys.service.impl;

import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import com.SWP391.horserace.jockeys.dto.InvitationInsightsResponse;
import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.dto.JockeyStatsResponse;
import com.SWP391.horserace.jockeys.dto.JockeySuggestionResponse;
import com.SWP391.horserace.jockeys.dto.UnassignedEntryResponse;
import com.SWP391.horserace.jockeys.dto.UpdateJockeyProfileRequest;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JockeyServiceImplTest {

    @Mock JockeyProfileRepository jockeyProfileRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;
    @Mock RaceRepository raceRepository;
    @Mock HorseRepository horseRepository;
    @Mock com.SWP391.horserace.races.repository.RaceResultRepository raceResultRepository;

    private JockeyServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new JockeyServiceImpl(
                jockeyProfileRepository, jockeyAssignmentRepository, raceRepository, horseRepository,
                raceResultRepository);
    }

    private JockeyProfile profile(UUID userId, BigDecimal winRate, BigDecimal rating, String recentForm) {
        User user = User.builder()
                .userId(userId)
                .userCode("USR-" + userId.toString().substring(0, 4))
                .fullName("Jockey " + userId.toString().substring(0, 4))
                .email("j@x.com")
                .status(UserStatus.ACTIVE)
                .build();
        return JockeyProfile.builder()
                .jockeyUserId(userId)
                .jockeyUser(user)
                .winCount(10)
                .winRate(winRate)
                .rating(rating)
                .recentForm(recentForm)
                .ridingStyle("Closer")
                .baseFee(new BigDecimal("12000.00"))
                .prizePercent(new BigDecimal("10.00"))
                .lastTrophy("Some Cup")
                .build();
    }

    // ── mapping of new JockeyResponse fields ──

    @Test
    void getAllJockeys_mapsNewMarketFields_andSplitsRecentFormCsv() {
        UUID jId = UUID.randomUUID();
        when(jockeyProfileRepository.findAllActiveJockeys())
                .thenReturn(List.of(profile(jId, new BigDecimal("62.50"), new BigDecimal("4.9"), "W,L,W,W,L")));

        List<JockeyResponse> result = service.getAllJockeys();

        assertThat(result).hasSize(1);
        JockeyResponse r = result.get(0);
        assertThat(r.getRating()).isEqualByComparingTo("4.9");
        assertThat(r.getWinRate()).isEqualByComparingTo("62.50");
        assertThat(r.getRidingStyle()).isEqualTo("Closer");
        assertThat(r.getBaseFee()).isEqualByComparingTo("12000.00");
        assertThat(r.getPrizePercent()).isEqualByComparingTo("10.00");
        assertThat(r.getLastTrophy()).isEqualTo("Some Cup");
        assertThat(r.getRecentForm()).containsExactly("W", "L", "W", "W", "L");
    }

    @Test
    void getAllJockeys_nullRecentForm_yieldsEmptyList() {
        UUID jId = UUID.randomUUID();
        when(jockeyProfileRepository.findAllActiveJockeys())
                .thenReturn(List.of(profile(jId, null, null, null)));

        JockeyResponse r = service.getAllJockeys().get(0);

        assertThat(r.getRecentForm()).isEmpty();
    }

    // ── unassigned entries ──

    @Test
    void getUnassignedEntries_nullOwner_unauthenticated() {
        assertThatThrownBy(() -> service.getUnassignedEntries(null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void getUnassignedEntries_mapsEntryToResponse() {
        UUID registrationId = UUID.randomUUID();
        OffsetDateTime raceDate = OffsetDateTime.now();

        Horse horse = Horse.builder().horseId(horseId).name("Starlight Glimmer").build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(registrationId).horse(horse).build();
        Race race = Race.builder().raceId(raceId).name("Dubai World Cup").scheduledStartAt(raceDate).build();
        RaceEntry entry = RaceEntry.builder().registration(reg).race(race).build();

        when(jockeyAssignmentRepository.findUnassignedEntriesByOwner(ownerId)).thenReturn(List.of(entry));

        List<UnassignedEntryResponse> result = service.getUnassignedEntries(ownerId);

        assertThat(result).hasSize(1);
        UnassignedEntryResponse e = result.get(0);
        assertThat(e.getRegistrationId()).isEqualTo(registrationId);
        assertThat(e.getHorseId()).isEqualTo(horseId);
        assertThat(e.getHorseName()).isEqualTo("Starlight Glimmer");
        assertThat(e.getRaceId()).isEqualTo(raceId);
        assertThat(e.getRaceName()).isEqualTo("Dubai World Cup");
        assertThat(e.getRaceDate()).isEqualTo(raceDate);
    }

    // ── jockey suggestions ──

    @Test
    void getJockeySuggestions_raceNotFound_throws() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJockeySuggestions(raceId, horseId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_NOT_FOUND);
    }

    @Test
    void getJockeySuggestions_horseNotFound_throws() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(Race.builder().raceId(raceId).build()));
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJockeySuggestions(raceId, horseId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HORSE_NOT_FOUND);
    }

    @Test
    void getJockeySuggestions_scoresInRange_sortedDesc_andDeterministic() {
        UUID j1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID j2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID j3 = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(Race.builder().raceId(raceId).build()));
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(Horse.builder().horseId(horseId).build()));
        when(jockeyProfileRepository.findAllActiveJockeys()).thenReturn(List.of(
                profile(j1, new BigDecimal("58.50"), new BigDecimal("4.7"), "W,L,W"),
                profile(j2, new BigDecimal("62.50"), new BigDecimal("4.9"), "W,W,W"),
                profile(j3, null, null, null)
        ));

        List<JockeySuggestionResponse> first = service.getJockeySuggestions(raceId, horseId);

        // All scores within 50–99.
        assertThat(first).allSatisfy(s -> assertThat(s.getCompatibility()).isBetween(50, 99));
        // Sorted descending.
        assertThat(first).extracting(JockeySuggestionResponse::getCompatibility).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
        // All jockeys returned.
        assertThat(first).extracting(JockeySuggestionResponse::getJockeyUserId).containsExactlyInAnyOrder(j1, j2, j3);

        // Deterministic: a second call with identical inputs yields identical scores per jockey.
        when(jockeyProfileRepository.findAllActiveJockeys()).thenReturn(List.of(
                profile(j1, new BigDecimal("58.50"), new BigDecimal("4.7"), "W,L,W"),
                profile(j2, new BigDecimal("62.50"), new BigDecimal("4.9"), "W,W,W"),
                profile(j3, null, null, null)
        ));
        List<JockeySuggestionResponse> second = service.getJockeySuggestions(raceId, horseId);
        for (UUID id : List.of(j1, j2, j3)) {
            int a = first.stream().filter(s -> s.getJockeyUserId().equals(id)).findFirst().orElseThrow().getCompatibility();
            int b = second.stream().filter(s -> s.getJockeyUserId().equals(id)).findFirst().orElseThrow().getCompatibility();
            assertThat(a).isEqualTo(b);
        }
    }

    // =========================================================================
    // #8 updateMyProfile
    // =========================================================================

    @Test
    void updateMyProfile_nullCaller_unauthenticated() {
        assertThatThrownBy(() -> service.updateMyProfile(null, new UpdateJockeyProfileRequest()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void updateMyProfile_noProfile_jockeyNotFound() {
        UUID caller = UUID.randomUUID();
        when(jockeyProfileRepository.findByIdAndUserActive(caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMyProfile(caller, new UpdateJockeyProfileRequest()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOCKEY_NOT_FOUND);
    }

    @Test
    void updateMyProfile_partialUpdate_onlyAppliesNonNullFields() {
        UUID caller = UUID.randomUUID();
        JockeyProfile existing = profile(caller, new BigDecimal("50.00"), new BigDecimal("4.0"), "W,L");
        existing.setRidingStyle("OldStyle");
        existing.setBio("old bio");
        existing.setBaseFee(new BigDecimal("1000.00"));
        when(jockeyProfileRepository.findByIdAndUserActive(caller)).thenReturn(Optional.of(existing));
        when(jockeyProfileRepository.save(any(JockeyProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateJockeyProfileRequest req = new UpdateJockeyProfileRequest();
        req.setRidingStyle("STALKER");
        req.setPrizePercent(new BigDecimal("15.00"));
        // bio and baseFee left null → must be preserved

        JockeyResponse result = service.updateMyProfile(caller, req);

        assertThat(result.getRidingStyle()).isEqualTo("STALKER");
        assertThat(result.getPrizePercent()).isEqualByComparingTo("15.00");
        assertThat(result.getBio()).isEqualTo("old bio");          // unchanged
        assertThat(result.getBaseFee()).isEqualByComparingTo("1000.00"); // unchanged
    }

    // =========================================================================
    // #1 getMyStats
    // =========================================================================

    private RaceEntry entryWithPrize(UUID entryId, BigDecimal prize) {
        return RaceEntry.builder().entryId(entryId).prizeEarned(prize).build();
    }

    private JockeyAssignment acceptedRide(RaceEntry entry) {
        return JockeyAssignment.builder()
                .status(JockeyAssignmentStatus.ACCEPTED)
                .entry(entry)
                .build();
    }

    private RaceResult result(RaceEntry entry, Integer pos) {
        return RaceResult.builder().entry(entry).finishPosition(pos).build();
    }

    @Test
    void getMyStats_zeroRides_returnsAllZeros() {
        UUID caller = UUID.randomUUID();
        JockeyProfile p = profile(caller, null, null, null);
        p.setWinCount(7);
        when(jockeyProfileRepository.findByIdAndUserActive(caller)).thenReturn(Optional.of(p));
        when(jockeyAssignmentRepository.findAcceptedRidesByJockey(caller)).thenReturn(List.of());

        JockeyStatsResponse stats = service.getMyStats(caller);

        assertThat(stats.getTotalRides()).isZero();
        assertThat(stats.getWins()).isZero();
        assertThat(stats.getWinRate()).isEqualTo(0.0);
        assertThat(stats.getTop3Rate()).isEqualTo(0.0);
        assertThat(stats.getAvgPlacement()).isEqualTo(0.0);
        assertThat(stats.getCareerWins()).isEqualTo(7);
        assertThat(stats.getCareerEarnings()).isEqualByComparingTo("0");
        assertThat(stats.getSeasonEarnings()).isEqualByComparingTo("0");
    }

    @Test
    void getMyStats_computesRatesAndEarnings() {
        UUID caller = UUID.randomUUID();
        JockeyProfile p = profile(caller, null, null, null);
        p.setWinCount(100);
        when(jockeyProfileRepository.findByIdAndUserActive(caller)).thenReturn(Optional.of(p));

        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID(), e3 = UUID.randomUUID(), e4 = UUID.randomUUID();
        RaceEntry en1 = entryWithPrize(e1, new BigDecimal("100"));   // pos 1
        RaceEntry en2 = entryWithPrize(e2, new BigDecimal("50"));    // pos 2
        RaceEntry en3 = entryWithPrize(e3, new BigDecimal("25"));    // pos 3
        RaceEntry en4 = entryWithPrize(e4, new BigDecimal("0"));     // no result → ignored

        when(jockeyAssignmentRepository.findAcceptedRidesByJockey(caller)).thenReturn(List.of(
                acceptedRide(en1), acceptedRide(en2), acceptedRide(en3), acceptedRide(en4)));
        when(raceResultRepository.findByEntry_EntryIdIn(anyCollection())).thenReturn(List.of(
                result(en1, 1), result(en2, 2), result(en3, 3)));  // en4 has no result

        JockeyStatsResponse stats = service.getMyStats(caller);

        assertThat(stats.getTotalRides()).isEqualTo(3);  // en4 excluded (no result)
        assertThat(stats.getWins()).isEqualTo(1);
        assertThat(stats.getPlaces()).isEqualTo(1);
        assertThat(stats.getWinRate()).isEqualTo(33.3);          // 1/3*100
        assertThat(stats.getTop3Rate()).isEqualTo(100.0);        // 3/3*100
        assertThat(stats.getAvgPlacement()).isEqualTo(2.0);      // (1+2+3)/3
        assertThat(stats.getCareerWins()).isEqualTo(100);
        assertThat(stats.getCareerEarnings()).isEqualByComparingTo("175"); // 100+50+25 (en4 excluded)
        assertThat(stats.getSeasonEarnings()).isEqualByComparingTo("175");
    }

    // =========================================================================
    // #11 getMyInvitationInsights
    // =========================================================================

    @Test
    void getMyInvitationInsights_computesRateAndTopOwners() {
        UUID caller = UUID.randomUUID();
        JockeyProfile p = profile(caller, null, null, null);
        when(jockeyProfileRepository.findByIdAndUserActive(caller)).thenReturn(Optional.of(p));

        when(jockeyAssignmentRepository.countInvitedBetween(eq(caller), any(), any()))
                .thenReturn(5L)   // this week
                .thenReturn(3L);  // prior week

        UUID ownerA = UUID.randomUUID(), ownerB = UUID.randomUUID();
        User uA = User.builder().userId(ownerA).fullName("Kingsway Elite").build();
        User uB = User.builder().userId(ownerB).fullName("Oakwood Stables").build();

        JockeyAssignment a1 = JockeyAssignment.builder().status(JockeyAssignmentStatus.ACCEPTED).assignedBy(uA).build();
        JockeyAssignment a2 = JockeyAssignment.builder().status(JockeyAssignmentStatus.DECLINED).assignedBy(uA).build();
        JockeyAssignment a3 = JockeyAssignment.builder().status(JockeyAssignmentStatus.ACCEPTED).assignedBy(uA).build();
        JockeyAssignment a4 = JockeyAssignment.builder().status(JockeyAssignmentStatus.INVITED).assignedBy(uB).build();
        when(jockeyAssignmentRepository.findAllByJockeyWithInviter(caller))
                .thenReturn(List.of(a1, a2, a3, a4));

        InvitationInsightsResponse insights = service.getMyInvitationInsights(caller);

        assertThat(insights.getInvitationsThisWeek()).isEqualTo(5);
        assertThat(insights.getWeekDelta()).isEqualTo(2);  // 5 - 3
        assertThat(insights.getAcceptanceRate()).isEqualTo(50);  // 2 accepted / 4 total
        assertThat(insights.getMostActiveOwners()).hasSize(2);
        // ownerA has 3 requests → first
        assertThat(insights.getMostActiveOwners().get(0).getOwnerUserId()).isEqualTo(ownerA);
        assertThat(insights.getMostActiveOwners().get(0).getName()).isEqualTo("Kingsway Elite");
        assertThat(insights.getMostActiveOwners().get(0).getRequests()).isEqualTo(3);
        assertThat(insights.getMostActiveOwners().get(1).getOwnerUserId()).isEqualTo(ownerB);
    }
}
