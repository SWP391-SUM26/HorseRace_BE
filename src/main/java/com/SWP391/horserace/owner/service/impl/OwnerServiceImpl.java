package com.SWP391.horserace.owner.service.impl;

import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.owner.dto.OwnerOverviewResponse;
import com.SWP391.horserace.owner.dto.OwnerRaceReportRow;
import com.SWP391.horserace.owner.service.OwnerService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerServiceImpl implements OwnerService {

    private final HorseRepository horseRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RaceResultRepository raceResultRepository;
    private final RegistrationRepository registrationRepository;
    private final com.SWP391.horserace.wallets.repository.WalletTransactionRepository walletTransactionRepository;

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

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getOwnerRaceIds(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return raceEntryRepository.findByOwnerUserId(ownerUserId).stream()
                .map(RaceEntry::getRace)
                .filter(r -> r != null)
                .map(Race::getRaceId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerRaceReportRow> getRaceReport(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        List<TournamentRegistration> regs = registrationRepository.findOwnerRaceRegistrations(ownerUserId);
        if (regs.isEmpty()) {
            return List.of();
        }

        // Batch-load entries by registration id, then results by entry id — two queries total
        // instead of 2N (see JockeyAssignmentServiceImpl.getMyRides for the same pattern).
        List<UUID> regIds = regs.stream().map(TournamentRegistration::getRegistrationId).toList();
        Map<UUID, RaceEntry> entryByRegId = raceEntryRepository.findByRegistration_RegistrationIdIn(regIds).stream()
                .filter(e -> e.getRegistration() != null)
                .collect(Collectors.toMap(e -> e.getRegistration().getRegistrationId(), Function.identity(), (a, b) -> a));

        List<UUID> entryIds = entryByRegId.values().stream().map(RaceEntry::getEntryId).toList();
        Map<UUID, RaceResult> resultByEntryId = entryIds.isEmpty()
                ? Map.of()
                : raceResultRepository.findByEntry_EntryIdIn(entryIds).stream()
                    .filter(r -> r.getEntry() != null)
                    .collect(Collectors.toMap(r -> r.getEntry().getEntryId(), Function.identity(), (a, b) -> a));

        return regs.stream()
                .map(reg -> mapToReportRow(reg, entryByRegId.get(reg.getRegistrationId()), resultByEntryId))
                .toList();
    }

    private OwnerRaceReportRow mapToReportRow(TournamentRegistration reg, RaceEntry entry,
                                              Map<UUID, RaceResult> resultByEntryId) {
        Race race = reg.getRace();
        Horse horse = reg.getHorse();
        // A registration becomes a race entry only on approval; no entry ⇒ "did not participate".
        RaceResult result = entry != null ? resultByEntryId.get(entry.getEntryId()) : null;
        // "Participated" (actually ran) = became an entry and was not scratched. A SCRATCHED entry
        // was withdrawn before the off, so it counts as "did not run"; DISQUALIFIED still ran.
        boolean participated = entry != null && entry.getStatus() != RaceEntryStatus.SCRATCHED;
        return OwnerRaceReportRow.builder()
                .raceId(race != null ? race.getRaceId() : null)
                .raceCode(race != null ? race.getRaceCode() : null)
                .raceName(race != null ? race.getName() : null)
                .raceStatus(race != null && race.getStatus() != null ? race.getStatus().name() : null)
                .scheduledStartAt(race != null ? race.getScheduledStartAt() : null)
                .tournamentName(reg.getTournament() != null ? reg.getTournament().getName() : null)
                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getName() : null)
                .registrationId(reg.getRegistrationId())
                .registrationCode(reg.getRegistrationCode())
                .registrationStatus(reg.getStatus() != null ? reg.getStatus().name() : null)
                .rejectionReason(reg.getRejectionReason())
                .entered(entry != null)
                .participated(participated)
                .entryStatus(entry != null && entry.getStatus() != null ? entry.getStatus().name() : null)
                .entryNo(entry != null ? entry.getEntryNo() : null)
                .finishPosition(result != null ? result.getFinishPosition() : null)
                .finishTimeMs(result != null ? result.getFinishTimeMs() : null)
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

    // ---------- Financial overview ----------

    /**
     * Ledger categories that are genuinely owner EARNINGS.
     *
     * DEPOSIT is deliberately absent: topping your own wallet up moves your money from a bank into
     * this app, it does not earn you anything. Counting it inflated "Total Earnings" by the exact
     * amount of every top-up, so a 10,000,000₫ deposit read as 10,000,000₫ of winnings.
     */
    private static final java.util.Set<com.SWP391.horserace.wallets.entity.TxnCategory> INCOME_CATEGORIES =
            java.util.EnumSet.of(com.SWP391.horserace.wallets.entity.TxnCategory.PRIZE,
                    com.SWP391.horserace.wallets.entity.TxnCategory.BET_PAYOUT,
                    com.SWP391.horserace.wallets.entity.TxnCategory.REFUND,
                    com.SWP391.horserace.wallets.entity.TxnCategory.REWARD);

    /**
     * Ledger categories that are genuinely owner COSTS.
     *
     * WITHDRAWAL is absent for the mirror-image reason DEPOSIT is: cashing out is not a business
     * expense, it is the same money leaving the app. JOCKEY_FEE is the owner's one real outgoing
     * now that entering a race is free; ENTRY_FEE stays so historical rows still read correctly.
     */
    private static final java.util.Set<com.SWP391.horserace.wallets.entity.TxnCategory> EXPENSE_CATEGORIES =
            java.util.EnumSet.of(com.SWP391.horserace.wallets.entity.TxnCategory.JOCKEY_FEE,
                    com.SWP391.horserace.wallets.entity.TxnCategory.ENTRY_FEE,
                    com.SWP391.horserace.wallets.entity.TxnCategory.BET_STAKE);

    @Override
    @Transactional(readOnly = true)
    public com.SWP391.horserace.owner.dto.OwnerFinanceResponse getFinances(UUID ownerUserId, int txnLimit) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        int limit = txnLimit <= 0 ? 20 : Math.min(txnLimit, 200);

        var pageAll = walletTransactionRepository
                .findByWallet_User_UserIdOrderByCreatedAtDesc(ownerUserId,
                        org.springframework.data.domain.PageRequest.of(0, 500));
        var ledger = pageAll.getContent();

        // Direction alone is not enough: a top-up is a CREDIT and a cash-out is a DEBIT, but neither
        // is trading performance. This loop used to sum every CREDIT as income and every DEBIT as
        // expense, ignoring the category sets below it, so moving money in and out of your own
        // wallet moved the headline KPIs. Both filters now apply.
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (var t : ledger) {
            var category = t.getTxnCategory();
            if (category == null) {
                continue;
            }
            if (t.getEntryType() == com.SWP391.horserace.wallets.entity.EntryType.CREDIT) {
                if (INCOME_CATEGORIES.contains(category)) {
                    income = income.add(nz(t.getAmount()));
                }
            } else if (EXPENSE_CATEGORIES.contains(category)) {
                expense = expense.add(nz(t.getAmount()));
            }
        }
        BigDecimal net = income.subtract(expense);
        BigDecimal margin = income.signum() == 0 ? BigDecimal.ZERO
                : net.multiply(ONE_HUNDRED).divide(income, 1, java.math.RoundingMode.HALF_UP);

        // Prize money per horse — race_entry.prize_earned is the column certify() actually writes.
        List<RaceEntry> entries = raceEntryRepository.findByOwnerUserId(ownerUserId);
        Map<String, BigDecimal> byHorse = new java.util.LinkedHashMap<>();
        Map<String, String> horseIdByName = new java.util.HashMap<>();
        for (RaceEntry e : entries) {
            TournamentRegistration reg = e.getRegistration();
            Horse h = reg != null ? reg.getHorse() : null;
            if (h == null) {
                continue;
            }
            byHorse.merge(h.getName(), nz(e.getPrizeEarned()), BigDecimal::add);
            horseIdByName.putIfAbsent(h.getName(), h.getHorseId().toString());
        }
        BigDecimal top = byHorse.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<com.SWP391.horserace.owner.dto.OwnerFinanceResponse.HorseProfitability> horses = byHorse.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(6)
                .map(en -> com.SWP391.horserace.owner.dto.OwnerFinanceResponse.HorseProfitability.builder()
                        .id(horseIdByName.get(en.getKey()))
                        .name(en.getKey())
                        .earnings(en.getValue())
                        // percent is a presentation value the FE renders as a bar width, so it has
                        // to be computed here rather than left to the page.
                        .percent(top.signum() == 0 ? BigDecimal.ZERO
                                : en.getValue().multiply(ONE_HUNDRED).divide(top, 0, java.math.RoundingMode.HALF_UP))
                        .build())
                .toList();

        List<com.SWP391.horserace.owner.dto.OwnerFinanceResponse.FinanceTransaction> txns = ledger.stream()
                .limit(limit)
                .map(t -> com.SWP391.horserace.owner.dto.OwnerFinanceResponse.FinanceTransaction.builder()
                        .id(t.getWalletTxnId().toString())
                        .date(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                        .description(describe(t))
                        .horse("—")
                        .category(INCOME_CATEGORIES.contains(t.getTxnCategory())
                                && t.getEntryType() == com.SWP391.horserace.wallets.entity.EntryType.CREDIT
                                ? "INCOME" : "EXPENSE")
                        .amount(nz(t.getAmount()))
                        .build())
                .toList();

        int year = java.time.Year.now().getValue();
        return com.SWP391.horserace.owner.dto.OwnerFinanceResponse.builder()
                .kpis(com.SWP391.horserace.owner.dto.OwnerFinanceResponse.Kpis.builder()
                        .totalEarnings(income)
                        .season(year + "-" + (year + 1))
                        // No historical snapshots exist to diff against, so trends are reported as 0
                        // rather than invented. Wire these up when period aggregates land.
                        .earningsTrend(BigDecimal.ZERO)
                        .pendingPayouts(BigDecimal.ZERO)
                        .pendingCount(0)
                        .pendingEtaDays(0)
                        .totalExpenses(expense)
                        .expensesTrend(BigDecimal.ZERO)
                        .netProfit(net)
                        .margin(margin)
                        .build())
                .horses(horses)
                .transactions(txns)
                .totalTransactions(pageAll.getTotalElements())
                .build();
    }

    private static String describe(com.SWP391.horserace.wallets.entity.WalletTransaction t) {
        return switch (t.getTxnCategory()) {
            case PRIZE -> "Tiền thưởng giải";
            case ENTRY_FEE -> "Phí tham dự cuộc đua";
            case BET_STAKE -> "Đặt cược";
            case BET_PAYOUT -> "Thắng cược";
            case REFUND -> "Hoàn tiền";
            case DEPOSIT -> "Nạp ví";
            case WITHDRAWAL -> "Rút ví";
            case REWARD -> "Phần thưởng";
            case ADJUSTMENT -> "Điều chỉnh";
            case SPONSOR -> "Tài trợ giải thưởng";
            case JOCKEY_FEE -> "Tiền thuê nài";
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
}
