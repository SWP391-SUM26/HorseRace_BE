package com.SWP391.horserace.predictions.service.impl;

import com.SWP391.horserace.predictions.dto.AdminPredictionResponse;
import com.SWP391.horserace.predictions.dto.PredictionFilterRequest;
import com.SWP391.horserace.predictions.dto.PredictionStatsResponse;
import com.SWP391.horserace.predictions.entity.BettingPoolStatus;
import com.SWP391.horserace.predictions.entity.Payout;
import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.repository.BettingPoolRepository;
import com.SWP391.horserace.predictions.repository.PayoutRepository;
import com.SWP391.horserace.predictions.repository.PredictionRepository;
import com.SWP391.horserace.predictions.repository.PredictionSpecification;
import com.SWP391.horserace.predictions.service.AdminPredictionService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPredictionServiceImpl implements AdminPredictionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PredictionRepository predictionRepository;
    private final PayoutRepository payoutRepository;
    private final BettingPoolRepository bettingPoolRepository;
    private final WalletLedgerService walletLedgerService;
    private final HouseWalletService houseWalletService;
    private final com.SWP391.horserace.notifications.service.NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPredictionResponse> list(PredictionFilterRequest filter) {
        return predictionRepository
                .findAll(PredictionSpecification.withFilters(filter), buildPageable(filter))
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PredictionStatsResponse stats() {
        List<Prediction> all = predictionRepository.findAll();
        long pending = 0, won = 0, lost = 0, voided = 0;
        BigDecimal stake = BigDecimal.ZERO;
        for (Prediction p : all) {
            stake = stake.add(nz(p.getStakeAmount()));
            switch (p.getStatus()) {
                case PENDING, CONFIRMED -> pending++;
                case WON -> won++;
                case LOST -> lost++;
                case VOID, REFUNDED -> voided++;
                default -> { /* no other states exist */ }
            }
        }
        BigDecimal paid = payoutRepository.findAll().stream()
                .map(Payout::getPayoutAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PredictionStatsResponse.builder()
                .total(all.size())
                .pending(pending)
                .won(won)
                .lost(lost)
                .voided(voided)
                .totalStake(stake)
                .totalPaidOut(paid)
                .build();
    }

    @Override
    @Transactional
    public AdminPredictionResponse voidPrediction(UUID adminUserId, UUID predictionId, String reason) {
        if (adminUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Prediction p = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new AppException(ErrorCode.PREDICTION_NOT_FOUND));

        // Only an unsettled bet can be voided. A WON bet has already moved money out of the house
        // wallet and has a Payout row; unwinding that is a resettle, not a void.
        if (p.getStatus() != PredictionStatus.PENDING && p.getStatus() != PredictionStatus.CONFIRMED) {
            throw new AppException(ErrorCode.PREDICTION_CANNOT_CANCEL);
        }

        Race race = p.getRace();
        if (race != null) {
            bettingPoolRepository
                    .findByRace_RaceIdAndPredictionType(race.getRaceId(), p.getPredictionType())
                    .ifPresent(pool -> {
                        if (pool.getStatus() == BettingPoolStatus.OPEN) {
                            pool.setTotalStake(nz(pool.getTotalStake()).subtract(nz(p.getStakeAmount())));
                            bettingPoolRepository.save(pool);
                        }
                    });
        }

        // Two-sided refund, HOUSE FIRST — same lock order as every other money move.
        UUID house = houseWalletService.houseUserId();
        UUID bettor = p.getSpectator() != null ? p.getSpectator().getUserId() : null;
        if (bettor != null && nz(p.getStakeAmount()).signum() > 0) {
            walletLedgerService.applyEntry(house, EntryType.DEBIT, TxnCategory.REFUND,
                    p.getStakeAmount(), "PREDICTION", p.getPredictionId());
            walletLedgerService.applyEntry(bettor, EntryType.CREDIT, TxnCategory.REFUND,
                    p.getStakeAmount(), "PREDICTION", p.getPredictionId());
        }

        p.setStatus(PredictionStatus.VOID);
        p.setSettledAt(java.time.OffsetDateTime.now());
        Prediction saved = predictionRepository.save(p);

        if (bettor != null) {
            notificationService.notifyUser(bettor, "Vé cược đã bị huỷ",
                    "Quản trị viên đã huỷ một vé cược của bạn và hoàn tiền đặt cược."
                            + (reason != null && !reason.isBlank() ? " Lý do: " + reason : ""));
        }
        return mapToResponse(saved);
    }

    // ── helpers ──

    private Pageable buildPageable(PredictionFilterRequest f) {
        int page = (f.getPage() != null && f.getPage() >= 0) ? f.getPage() : 0;
        int size = (f.getSize() != null && f.getSize() > 0) ? Math.min(f.getSize(), MAX_PAGE_SIZE) : 10;
        String field = switch (f.getSortBy() != null ? f.getSortBy().trim().toLowerCase() : "submittedat") {
            case "stakeamount", "stake" -> "stakeAmount";
            case "settledat" -> "settledAt";
            case "status" -> "status";
            default -> "submittedAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(f.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private AdminPredictionResponse mapToResponse(Prediction p) {
        Race race = p.getRace();
        User u = p.getSpectator();
        RaceEntry entry = p.getPredictedEntry();
        String horseName = entry != null && entry.getRegistration() != null
                && entry.getRegistration().getHorse() != null
                ? entry.getRegistration().getHorse().getName() : null;
        BigDecimal payout = payoutRepository.findAll().stream()
                .filter(x -> x.getPrediction() != null
                        && p.getPredictionId().equals(x.getPrediction().getPredictionId()))
                .map(Payout::getPayoutAmount)
                .findFirst().orElse(null);

        return AdminPredictionResponse.builder()
                .predictionId(p.getPredictionId())
                .raceId(race != null ? race.getRaceId() : null)
                .raceCode(race != null ? race.getRaceCode() : null)
                .raceName(race != null ? race.getName() : null)
                .spectatorUserId(u != null ? u.getUserId() : null)
                .spectatorName(u != null ? u.getFullName() : null)
                .spectatorEmail(u != null ? u.getEmail() : null)
                .predictionType(p.getPredictionType() != null ? p.getPredictionType().name() : null)
                .horseName(horseName)
                .stakeAmount(p.getStakeAmount())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .payoutAmount(payout)
                .submittedAt(p.getSubmittedAt())
                .settledAt(p.getSettledAt())
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
