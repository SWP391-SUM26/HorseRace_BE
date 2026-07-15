package com.SWP391.horserace.shared.exception;

/**
 * One failing field in a validation error (FR-09). The merged validation handler returns a
 * {@code List<FieldErrorItem>} as the {@code ApiResponse.data} so a 400 names EVERY invalid field.
 */
public record FieldErrorItem(String field, String message) {
}
