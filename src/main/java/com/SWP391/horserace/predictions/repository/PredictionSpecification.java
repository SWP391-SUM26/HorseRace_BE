package com.SWP391.horserace.predictions.repository;

import com.SWP391.horserace.predictions.dto.PredictionFilterRequest;
import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.entity.PredictionType;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filter → Specification for the admin prediction table. Mirrors {@code RegistrationSpecification}. */
public final class PredictionSpecification {

    private PredictionSpecification() {
    }

    public static Specification<Prediction> withFilters(PredictionFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            var race = root.join("race", JoinType.LEFT);
            var spectator = root.join("spectator", JoinType.LEFT);

            if (f.getQ() != null && !f.getQ().isBlank()) {
                String like = "%" + f.getQ().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(spectator.get("fullName")), like),
                        cb.like(cb.lower(spectator.get("email")), like),
                        cb.like(cb.lower(race.get("raceCode")), like),
                        cb.like(cb.lower(race.get("name")), like)));
            }
            if (f.getStatus() != null && !f.getStatus().isBlank()) {
                // An unrecognised status matches nothing rather than 500-ing on the enum parse.
                PredictionStatus st = parse(PredictionStatus.class, f.getStatus());
                predicates.add(st == null ? cb.disjunction() : cb.equal(root.get("status"), st));
            }
            if (f.getPredictionType() != null && !f.getPredictionType().isBlank()) {
                PredictionType pt = parse(PredictionType.class, f.getPredictionType());
                predicates.add(pt == null ? cb.disjunction() : cb.equal(root.get("predictionType"), pt));
            }
            if (f.getRaceId() != null) {
                predicates.add(cb.equal(race.get("raceId"), f.getRaceId()));
            }
            if (f.getSpectatorUserId() != null) {
                predicates.add(cb.equal(spectator.get("userId"), f.getSpectatorUserId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
