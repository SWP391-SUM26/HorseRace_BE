package com.SWP391.horserace.horses.service.impl;

import com.SWP391.horserace.horses.dto.AssignHorseToRaceRequest;
import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.horses.dto.RaceHistoryItemResponse;
import com.SWP391.horserace.horses.dto.UpdateMedicalStatusRequest;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseGender;
import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import com.SWP391.horserace.horses.entity.HorseStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseServiceImplTest {

    @Mock HorseRepository horseRepository;
    @Mock UserRepository userRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock RaceResultRepository raceResultRepository;

    private HorseServiceImpl service;

    private final UUID ownerId = UUID.randomUUID();
    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder().userId(ownerId).fullName("Owen Owner").build();
        // ImageUploadService is a concrete class (not mockable on this JVM); use a real instance
        // over a mocked storage — it is only exercised by updateHorseImage, which isn't tested here.
        service = new HorseServiceImpl(horseRepository, userRepository,
                new ImageUploadService(Mockito.mock(FileStorageService.class)),
                registrationRepository, raceRepository, raceEntryRepository, raceResultRepository);
    }

    private static HorseRequest req(String name, HorseGender gender) {
        return new HorseRequest(name, null, gender, "Arabian", null, null, null, null, null, null, null);
    }

    @Test
    void create_blankName_rejected() {
        assertThatThrownBy(() -> service.createHorse(ownerId, req("   ", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HORSE_NAME_REQUIRED);
    }

    @Test
    void create_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.createHorse(null, req("Midnight", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void create_setsOwnerGeneratesCodeAndDefaultStatus() {
        when(userRepository.findByUserIdAndDeletedFalse(ownerId)).thenReturn(Optional.of(owner));
        when(horseRepository.count()).thenReturn(4L);
        when(horseRepository.existsByHorseCode(any())).thenReturn(false);
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        HorseResponse res = service.createHorse(ownerId, req("Midnight Thunder", HorseGender.MALE));

        assertThat(res.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(res.getHorseCode()).isEqualTo("HRS0005");
        assertThat(res.getStatus()).isEqualTo(HorseStatus.ACTIVE);
        assertThat(res.getGender()).isEqualTo(HorseGender.MALE);
        assertThat(res.getName()).isEqualTo("Midnight Thunder");
    }

    @Test
    void getById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHorseById(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HORSE_NOT_FOUND);
    }

    @Test
    void getById_mapsEligibilityFields() {
        UUID id = UUID.randomUUID();
        java.time.OffsetDateTime expires = java.time.OffsetDateTime.now().plusDays(180);
        java.time.LocalDate coggins = java.time.LocalDate.now().minusDays(90);
        Horse horse = Horse.builder()
                .horseId(id).owner(owner).horseCode("HRS0001").name("Midnight Thunder")
                .fitnessCertified(true)
                .fitnessCertExpiresAt(expires)
                .passportScanStatus("VALID")
                .cogginsTestDate(coggins)
                .build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));

        HorseResponse res = service.getHorseById(id);

        assertThat(res.isFitnessCertified()).isTrue();
        assertThat(res.getFitnessCertExpiresAt()).isEqualTo(expires);
        assertThat(res.getPassportScanStatus()).isEqualTo("VALID");
        assertThat(res.getCogginsTestDate()).isEqualTo(coggins);
    }

    @Test
    void update_byNonOwnerNonAdmin_rejected() {
        UUID id = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(id).owner(owner).name("x").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));

        UUID strangerId = UUID.randomUUID();
        User stranger = User.builder().userId(strangerId)
                .role(Role.builder().roleCode("HORSE_OWNER").build()).build();
        when(userRepository.findByUserIdAndDeletedFalse(strangerId)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.updateHorse(strangerId, id, req("New", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_HORSE_OWNER);
    }

    @Test
    void update_byAdmin_allowed() {
        UUID id = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(id).owner(owner).name("x").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));

        UUID adminId = UUID.randomUUID();
        User admin = User.builder().userId(adminId)
                .role(Role.builder().roleCode("ADMIN").build()).build();
        when(userRepository.findByUserIdAndDeletedFalse(adminId)).thenReturn(Optional.of(admin));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        HorseResponse res = service.updateHorse(adminId, id, req("New Name", null));

        assertThat(res.getName()).isEqualTo("New Name");
    }

    @Test
    void delete_byOwner_softDeletes() {
        UUID id = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(id).owner(owner).name("x").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        service.deleteHorse(ownerId, id);

        assertThat(horse.isDeleted()).isTrue();
        assertThat(horse.getDeletedAt()).isNotNull();
    }

    // ── medical status ──

    @Test
    void getMedicalStatus_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMedicalStatus(id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HORSE_NOT_FOUND);
    }

    @Test
    void updateMedicalStatus_byOwner_appliesAndStampsCheckTime() {
        UUID id = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(id).owner(owner).name("Bolt").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        MedicalStatusResponse res = service.updateMedicalStatus(ownerId, id,
                new UpdateMedicalStatusRequest(HorseHealthStatus.INJURED, "Sore left foreleg"));

        assertThat(res.getHealthStatus()).isEqualTo(HorseHealthStatus.INJURED);
        assertThat(res.getMedicalNote()).isEqualTo("Sore left foreleg");
        assertThat(res.getLastHealthCheckAt()).isNotNull();
        assertThat(horse.getLastHealthCheckAt()).isNotNull();
    }

    @Test
    void updateMedicalStatus_byNonOwnerNonAdmin_rejected() {
        UUID id = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(id).owner(owner).name("Bolt").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));

        UUID strangerId = UUID.randomUUID();
        User stranger = User.builder().userId(strangerId)
                .role(Role.builder().roleCode("HORSE_OWNER").build()).build();
        when(userRepository.findByUserIdAndDeletedFalse(strangerId)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.updateMedicalStatus(strangerId, id,
                new UpdateMedicalStatusRequest(HorseHealthStatus.HEALTHY, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_HORSE_OWNER);
    }

    // ── race history ──

    @Test
    void getRaceHistory_mapsEntries() {
        UUID id = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(id).owner(owner).name("Bolt").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));

        Tournament t = Tournament.builder().tournamentId(UUID.randomUUID()).name("Spring Cup").build();
        Race race = Race.builder().raceId(UUID.randomUUID()).raceCode("RACE00001")
                .name("Opening Sprint").tournament(t).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(UUID.randomUUID()).horse(horse).tournament(t).build();
        RaceEntry entry = RaceEntry.builder().entryId(UUID.randomUUID()).entryCode("ENT00001")
                .race(race).registration(reg).status(RaceEntryStatus.ENTERED).build();

        when(raceEntryRepository.findHistoryByHorseId(id)).thenReturn(List.of(entry));
        when(raceResultRepository.findByEntry_EntryIdIn(anyCollection())).thenReturn(List.of());

        List<RaceHistoryItemResponse> history = service.getRaceHistory(id);

        assertThat(history).hasSize(1);
        RaceHistoryItemResponse item = history.get(0);
        assertThat(item.getRaceCode()).isEqualTo("RACE00001");
        assertThat(item.getRaceName()).isEqualTo("Opening Sprint");
        assertThat(item.getTournamentName()).isEqualTo("Spring Cup");
        assertThat(item.getEntryStatus()).isEqualTo("ENTERED");
        assertThat(item.getEntryCode()).isEqualTo("ENT00001");
        assertThat(item.getFinishPosition()).isNull();
    }

    // ── assign to race ──

    private Horse ownedHorse(UUID id) {
        return Horse.builder().horseId(id).owner(owner).name("Bolt").build();
    }

    @Test
    void assignHorseToRace_raceNotOpen_rejected() {
        UUID horseId = UUID.randomUUID();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(ownedHorse(horseId)));

        UUID raceId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).status(RaceStatus.FINISHED)
                .tournament(Tournament.builder().tournamentId(UUID.randomUUID()).build()).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.assignHorseToRace(ownerId, horseId,
                new AssignHorseToRaceRequest(raceId, null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_NOT_OPEN_FOR_ENTRY);
    }

    @Test
    void assignHorseToRace_noApprovedRegistration_rejected() {
        UUID horseId = UUID.randomUUID();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(ownedHorse(horseId)));

        UUID raceId = UUID.randomUUID();
        UUID tournamentId = UUID.randomUUID();
        Race race = Race.builder().raceId(raceId).status(RaceStatus.OPEN)
                .tournament(Tournament.builder().tournamentId(tournamentId).build()).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(registrationRepository.findFirstByHorse_HorseIdAndTournament_TournamentIdAndStatus(
                horseId, tournamentId, RegistrationStatus.APPROVED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignHorseToRace(ownerId, horseId,
                new AssignHorseToRaceRequest(raceId, null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HORSE_NO_APPROVED_REGISTRATION);
    }

    @Test
    void assignHorseToRace_happyPath_createsEnteredEntryWithCode() {
        UUID horseId = UUID.randomUUID();
        Horse horse = ownedHorse(horseId);
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));

        UUID raceId = UUID.randomUUID();
        UUID tournamentId = UUID.randomUUID();
        Tournament t = Tournament.builder().tournamentId(tournamentId).name("Spring Cup").build();
        Race race = Race.builder().raceId(raceId).status(RaceStatus.SCHEDULED).tournament(t).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));

        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(UUID.randomUUID()).horse(horse).owner(owner).tournament(t)
                .status(RegistrationStatus.APPROVED).build();
        when(registrationRepository.findFirstByHorse_HorseIdAndTournament_TournamentIdAndStatus(
                horseId, tournamentId, RegistrationStatus.APPROVED)).thenReturn(Optional.of(reg));

        when(raceEntryRepository.count()).thenReturn(0L);
        when(raceEntryRepository.existsByEntryCode(any())).thenReturn(false);
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(i -> i.getArgument(0));

        RaceEntryResponse res = service.assignHorseToRace(ownerId, horseId,
                new AssignHorseToRaceRequest(raceId, 3, 7));

        assertThat(res.getStatus()).isEqualTo(RaceEntryStatus.ENTERED);
        assertThat(res.getEntryCode()).isEqualTo("ENT00001");
        assertThat(res.getRaceId()).isEqualTo(raceId);
        assertThat(res.getLaneNo()).isEqualTo(3);
        assertThat(res.getEntryNo()).isEqualTo(7);
        assertThat(res.getHorseId()).isEqualTo(horseId);
    }
}
