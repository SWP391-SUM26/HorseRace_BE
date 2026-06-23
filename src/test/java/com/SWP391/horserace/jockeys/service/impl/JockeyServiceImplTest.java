package com.SWP391.horserace.jockeys.service.impl;

import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.dto.JockeySuggestionResponse;
import com.SWP391.horserace.jockeys.dto.UnassignedEntryResponse;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JockeyServiceImplTest {

    @Mock JockeyProfileRepository jockeyProfileRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;
    @Mock RaceRepository raceRepository;
    @Mock HorseRepository horseRepository;

    private JockeyServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new JockeyServiceImpl(
                jockeyProfileRepository, jockeyAssignmentRepository, raceRepository, horseRepository);
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
}
