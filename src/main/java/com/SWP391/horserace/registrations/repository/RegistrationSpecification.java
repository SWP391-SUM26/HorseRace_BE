package com.SWP391.horserace.registrations.repository;

import com.SWP391.horserace.registrations.dto.RegistrationFilterRequest;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a dynamic WHERE clause from {@link RegistrationFilterRequest}. Each provided filter adds an
 * AND predicate; the free-text {@code q} matches (OR) the registration code, horse name and
 * tournament name. Sorting/paging are applied by the caller via {@code Pageable}.
 */
public final class RegistrationSpecification {

    private RegistrationSpecification() { /* utility */ }

    public static Specification<TournamentRegistration> withFilters(RegistrationFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (f.getQ() != null && !f.getQ().isBlank()) {
                String like = "%" + f.getQ().trim().toLowerCase() + "%";
                Join<Object, Object> horse = root.join("horse", JoinType.LEFT);
                Join<Object, Object> tournament = root.join("tournament", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("registrationCode")), like),
                        cb.like(cb.lower(horse.get("name")), like),
                        cb.like(cb.lower(tournament.get("name")), like)));
            }
            if (f.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), f.getStatus()));
            }
            if (f.getTournamentId() != null) {
                predicates.add(cb.equal(root.get("tournament").get("tournamentId"), f.getTournamentId()));
            }
            if (f.getHorseId() != null) {
                predicates.add(cb.equal(root.get("horse").get("horseId"), f.getHorseId()));
            }
            if (f.getOwnerUserId() != null) {
                predicates.add(cb.equal(root.get("owner").get("userId"), f.getOwnerUserId()));
            }
            if (f.getCategory() != null && !f.getCategory().isBlank()) {
                predicates.add(cb.equal(root.get("category"), f.getCategory().trim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
