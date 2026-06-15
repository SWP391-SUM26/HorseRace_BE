package com.SWP391.horserace.predictions.entity;

/**
 * Mirrors the CHECK constraint on prediction.prediction_type and betting_pool.prediction_type
 * in db/schema_v4.sql. Names MUST match the DB values.
 */
public enum PredictionType {
    WIN,
    PLACE,
    SHOW,
    EXACTA,
    QUINELLA
}
