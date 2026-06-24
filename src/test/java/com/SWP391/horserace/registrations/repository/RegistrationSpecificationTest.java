package com.SWP391.horserace.registrations.repository;

import com.SWP391.horserace.registrations.dto.RegistrationFilterRequest;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationSpecificationTest {

    @Mock Root<TournamentRegistration> root;
    @Mock CriteriaQuery<?> query;
    @Mock CriteriaBuilder cb;
    @Mock Path<Object> categoryPath;
    @Mock Predicate predicate;

    @Test
    void category_set_addsEqualsPredicate_trimmed() {
        when(root.<Object>get("category")).thenReturn(categoryPath);
        when(cb.equal(any(), eq("GROUP_1"))).thenReturn(predicate);
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        RegistrationFilterRequest f = RegistrationFilterRequest.builder().category("  GROUP_1  ").build();
        RegistrationSpecification.withFilters(f).toPredicate(root, query, cb);

        verify(cb).equal(categoryPath, "GROUP_1"); // blank-trimmed, equals on category path
    }

    @Test
    void category_blank_ignored() {
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        RegistrationFilterRequest f = RegistrationFilterRequest.builder().category("   ").build();
        RegistrationSpecification.withFilters(f).toPredicate(root, query, cb);

        verify(root, never()).get("category");
        verify(cb, never()).equal(any(), eq("GROUP_1"));
    }
}
