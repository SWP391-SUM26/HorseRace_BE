package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import com.SWP391.horserace.races.dto.ResultFilterRequest;
import com.SWP391.horserace.races.entity.RaceResult;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class RaceResultSpecification {

    public static Specification<RaceResult> filter(ResultFilterRequest filter) {
        return (root, query, cb) -> {
            
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("race", JoinType.LEFT);
                var entryFetch = root.fetch("entry", JoinType.LEFT);
                var regFetch = entryFetch.fetch("registration", JoinType.LEFT);
                regFetch.fetch("horse", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getRaceId() != null) {
                predicates.add(cb.equal(root.get("race").get("raceId"), filter.getRaceId()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("officialityStatus"), filter.getStatus()));
            }

            if (StringUtils.hasText(filter.getQ())) {
                String likePattern = "%" + filter.getQ().toLowerCase() + "%";
                Predicate horseMatch = cb.like(
                        cb.lower(root.join("entry", JoinType.LEFT)
                                .join("registration", JoinType.LEFT)
                                .join("horse", JoinType.LEFT)
                                .get("name")), likePattern);
                                
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<JockeyAssignment> jaRoot = subquery.from(JockeyAssignment.class);
                subquery.select(cb.literal(1))
                        .where(
                                cb.equal(jaRoot.get("entry"), root.get("entry")),
                                cb.equal(jaRoot.get("status"), JockeyAssignmentStatus.ACCEPTED),
                                cb.like(cb.lower(jaRoot.join("jockey", JoinType.INNER).get("fullName")), likePattern)
                        );
                Predicate jockeyMatch = cb.exists(subquery);

                Predicate raceMatch = cb.like(
                        cb.lower(root.join("race", JoinType.LEFT).get("name")), likePattern);
                Predicate raceCodeMatch = cb.like(
                        cb.lower(root.join("race", JoinType.LEFT).get("raceCode")), likePattern);

                predicates.add(cb.or(horseMatch, jockeyMatch, raceMatch, raceCodeMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
