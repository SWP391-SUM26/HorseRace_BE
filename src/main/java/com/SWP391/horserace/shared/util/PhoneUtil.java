package com.SWP391.horserace.shared.util;

/**
 * Phone-number normalization used before every phone-uniqueness check (FR-11). Strips whitespace and
 * dashes so formatting variants (e.g. {@code "09 12-345-678"}) cannot bypass the dedupe against a
 * stored digits-only value. The strict Vietnamese {@code @Pattern} on the register DTOs already
 * rejects internal spaces/dashes at the edge — this is the service-layer safety net (and the value
 * actually persisted).
 */
public final class PhoneUtil {

    private PhoneUtil() {
    }

    /** Returns the phone with spaces and dashes removed, or {@code null} if the input is {@code null}. */
    public static String normalize(String phone) {
        return phone == null ? null : phone.replaceAll("[\\s-]", "");
    }
}
