package com.SWP391.horserace.horses.repository;

import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.entity.Horse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a dynamic WHERE clause from {@link HorseFilterRequest}. Always restricts to non-deleted
 * horses; each non-blank filter adds an AND predicate. Sorting/paging are applied by the caller
 * via {@code Pageable}, so this only contributes predicates.
 */
public final class HorseSpecification {

    private HorseSpecification() { /* utility */ }

    public static Specification<Horse> withFilters(HorseFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            if (f.getQ() != null && !f.getQ().isBlank()) {
                String like = "%" + f.getQ().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("horseCode")), like),
                        cb.like(cb.lower(root.get("microchipNo")), like)));
            }
            if (f.getStatus() != null && !f.getStatus().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), f.getStatus().trim().toUpperCase()));
            }
            if (f.getGender() != null && !f.getGender().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("gender")), f.getGender().trim().toUpperCase()));
            }
            if (f.getBreed() != null && !f.getBreed().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("breed")), "%" + f.getBreed().trim().toLowerCase() + "%"));
            }
            if (f.getOwnerUserId() != null) {
                predicates.add(cb.equal(root.get("owner").get("userId"), f.getOwnerUserId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
