package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.wallets.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseWalletServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock WalletService walletService;

    private HouseWalletServiceImpl service;

    private final String houseEmail = "admin@horserace.local";
    private final UUID houseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HouseWalletServiceImpl(userRepository, walletService);
        ReflectionTestUtils.setField(service, "houseEmail", houseEmail);
    }

    @Test
    void houseUserId_resolvesConfiguredEmail_andEnsuresWallet() {
        User house = User.builder().userId(houseId).email(houseEmail).build();
        when(userRepository.findByEmail(houseEmail)).thenReturn(Optional.of(house));

        UUID result = service.houseUserId();

        assertThat(result).isEqualTo(houseId);
        // The house wallet row is ensured inside the (money) tx so the credit/debit has a target.
        verify(walletService).getOrCreateWallet(houseId);
    }

    @Test
    void houseUserId_missingUser_throwsHouseWalletUnavailable_andNeverTouchesWallet() {
        when(userRepository.findByEmail(houseEmail)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.houseUserId())
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.HOUSE_WALLET_UNAVAILABLE);

        // No user -> no wallet resolution, no money can move.
        verifyNoInteractions(walletService);
    }
}
