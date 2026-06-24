package com.SWP391.horserace.inspections.dto;

/** Body for PATCH /api/v1/races/{raceId}/inspections/submit-all. */
public record SubmitAllRequest(Boolean confirm) {
}
