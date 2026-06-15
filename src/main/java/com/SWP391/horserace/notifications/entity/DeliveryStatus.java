package com.SWP391.horserace.notifications.entity;

/** Mirrors the CHECK constraint on notification.delivery_status in db/schema_v4.sql. Names MUST match DB values. */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
