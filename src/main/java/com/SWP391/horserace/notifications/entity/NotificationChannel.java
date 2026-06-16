package com.SWP391.horserace.notifications.entity;

/** Mirrors the CHECK constraint on notification.channel in db/schema_v4.sql. Names MUST match DB values. */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    SMS,
    PUSH
}
