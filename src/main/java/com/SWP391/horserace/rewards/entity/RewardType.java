package com.SWP391.horserace.rewards.entity;

public enum RewardType {
    DAILY_LOGIN,
    MILESTONE,
    PROMOTION,
    REFERRAL,
    COMPENSATION,
    /**
     * Raised by settlement when a prediction wins, so "nhận thông báo thưởng dự đoán" has a real
     * producer. Values here must stay in sync with the {@code reward_type} CHECK in schema_v4.sql.
     */
    BET_WIN
}
