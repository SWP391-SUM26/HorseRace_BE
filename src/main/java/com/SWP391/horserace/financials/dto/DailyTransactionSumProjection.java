package com.SWP391.horserace.financials.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyTransactionSumProjection {
    LocalDate getTxnDate();
    String getTxnCategory();
    BigDecimal getTotalAmount();
}
