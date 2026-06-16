package com.SWP391.horserace.horses.service.impl;

import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseGender;
import com.SWP391.horserace.horses.entity.HorseStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseServiceImplTest {

    @Mock HorseRepository horseRepository;
    @Mock UserRepository userRepository;
    @Mock FileStorageService fileStorageService;

    @InjectMocks HorseServiceImpl service;

    private final UUID ownerId = UUID.randomUUID();
    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder().userId(ownerId).fullName("Owen Owner").build();
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
    void update_byNonOwner_rejected() {
        UUID id = UUID.randomUUID();
        Horse horse = Horse.builder().horseId(id).owner(owner).name("x").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(id)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> service.updateHorse(UUID.randomUUID(), id, req("New", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_HORSE_OWNER);
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
}
