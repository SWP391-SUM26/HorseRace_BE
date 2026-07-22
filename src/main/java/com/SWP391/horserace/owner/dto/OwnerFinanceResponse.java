package com.SWP391.horserace.owner.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Owner financial overview. Field names mirror the FE's existing `FinanceOverview` type exactly so
 * the page could switch from its mock to this endpoint without any UI rework.
 *
 * <p>Everything is derived from the wallet ledger, which is the only record of money that the
 * application actually maintains: PRIZE / BET_PAYOUT / REFUND credits are income, ENTRY_FEE /
 * BET_STAKE debits are expense.
 */
@Data
@Builder
public class OwnerFinanceResponse {

    private Kpis kpis;
    private List<HorseProfitability> horses;
    private List<FinanceTransaction> transactions;
    /** Total ledger rows behind the paginated table. */
    private long totalTransactions;

    @Data
    @Builder
    public static class Kpis {
        private BigDecimal totalEarnings;
        private String season;
        /** Percent change vs the previous equivalent period; 0 when there is no history to compare. */
        private BigDecimal earningsTrend;
        private BigDecimal pendingPayouts;
        private long pendingCount;
        private int pendingEtaDays;
        private BigDecimal totalExpenses;
        private BigDecimal expensesTrend;
        private BigDecimal netProfit;
        /** Net profit as a percentage of income; 0 when there is no income. */
        private BigDecimal margin;
    }

    @Data
    @Builder
    public static class HorseProfitability {
        private String id;
        private String name;
        private BigDecimal earnings;
        /** 0–100, relative to the top earner — the FE renders it directly as a bar width. */
        private BigDecimal percent;
    }

    @Data
    @Builder
    public static class FinanceTransaction {
        private String id;
        private String date;
        private String description;
        private String horse;
        /** INCOME or EXPENSE — the FE derives the +/- sign from this, amounts stay positive. */
        private String category;
        private BigDecimal amount;
    }
}
