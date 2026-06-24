package com.SWP391.horserace.horses.repository;

import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.entity.Horse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a dynamic WHERE clause from {@link HorseFilterRequest}. Always restricts to non-deleted
 * horses; each provided filter adds an AND predicate. Sorting/paging are applied by the caller via
 * {@code Pageable}, so this only contributes predicates.
 */
public final class HorseSpecification {

    private HorseSpecification() { /* utility */ }

    /**
     * @param ownerUserId already-resolved owner id (the {@code ?ownerUserId=me} sentinel is
     *                    resolved to the caller's id in the service before this is built); may be null.
     */
    public static Specification<Horse> withFilters(HorseFilterRequest f, UUID ownerUserId) {
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
            if (f.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), f.getStatus()));
            }
            if (f.getGender() != null) {
                predicates.add(cb.equal(root.get("gender"), f.getGender()));
            }
            if (f.getBreed() != null && !f.getBreed().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("breed")), "%" + f.getBreed().trim().toLowerCase() + "%"));
            }
            if (ownerUserId != null) {
                predicates.add(cb.equal(root.get("owner").get("userId"), ownerUserId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
