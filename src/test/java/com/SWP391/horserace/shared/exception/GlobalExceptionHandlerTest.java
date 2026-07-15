package com.SWP391.horserace.shared.exception;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.auth.dto.RegisterJockeyRequest;
import com.SWP391.horserace.auth.dto.RegisterOwnerRequest;
import com.SWP391.horserace.auth.dto.RegisterSpectatorRequest;
import com.SWP391.horserace.horses.dto.AssignHorseToRaceRequest;
import com.SWP391.horserace.races.controller.EntryDocumentReviewController;
import com.SWP391.horserace.races.controller.RaceResultController;
import com.SWP391.horserace.races.dto.AssignParticipantRequest;
import com.SWP391.horserace.races.dto.RejectEntryRequest;
import com.SWP391.horserace.tournaments.dto.EligibilityDto;
import com.SWP391.horserace.tournaments.dto.TournamentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the merged validation handler (FR-09). Constructs the handler directly and feeds a
 * real {@link MethodArgumentNotValidException} built from a {@link BeanPropertyBindingResult}. Proves
 * the handler returns EVERY failing field (not just the first), resolves ErrorCode enum-name messages
 * per-field, passes literal messages through, and always answers 400.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Dummy method to back a {@link MethodParameter} for the exception. */
    @SuppressWarnings("unused")
    private void dummy(String s) {
    }

    private MethodArgumentNotValidException manv(FieldError... errors) {
        try {
            Method m = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
            MethodParameter mp = new MethodParameter(m, 0);
            BindingResult br = new BeanPropertyBindingResult(new Object(), "target");
            for (FieldError e : errors) {
                br.addError(e);
            }
            return new MethodArgumentNotValidException(mp, br);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void handlingValidation_returnsAllFieldErrors_notJustFirst() {
        MethodArgumentNotValidException ex = manv(
                new FieldError("target", "name", "Name must not exceed 255 characters"),
                new FieldError("target", "totalPurse", "Total purse cannot be negative"));

        ResponseEntity<ApiResponse<List<FieldErrorItem>>> resp = handler.handlingValidation(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        ApiResponse<List<FieldErrorItem>> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getData()).hasSize(2);
        assertThat(body.getData()).extracting(FieldErrorItem::field)
                .containsExactly("name", "totalPurse");
    }

    @Test
    void handlingValidation_resolvesErrorCodeEnumNameMessage() {
        MethodArgumentNotValidException ex = manv(
                new FieldError("target", "password", "WEAK_PASSWORD"));

        ResponseEntity<ApiResponse<List<FieldErrorItem>>> resp = handler.handlingValidation(ex);

        assertThat(resp.getBody().getData()).hasSize(1);
        assertThat(resp.getBody().getData().get(0).message())
                .isEqualTo(ErrorCode.WEAK_PASSWORD.getMessage());
    }

    @Test
    void handlingValidation_keepsLiteralMessage_whenNotAnEnumName() {
        MethodArgumentNotValidException ex = manv(
                new FieldError("target", "name", "Name must not exceed 255 characters"));

        ResponseEntity<ApiResponse<List<FieldErrorItem>>> resp = handler.handlingValidation(ex);

        assertThat(resp.getBody().getData().get(0).message())
                .isEqualTo("Name must not exceed 255 characters");
    }

    @Test
    void handlingValidation_messageField_isFirstResolvedError() {
        MethodArgumentNotValidException ex = manv(
                new FieldError("target", "password", "WEAK_PASSWORD"),
                new FieldError("target", "name", "Name must not exceed 255 characters"));

        ResponseEntity<ApiResponse<List<FieldErrorItem>>> resp = handler.handlingValidation(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().isSuccess()).isFalse();
        // message equals the FIRST item's resolved message (the ErrorCode message, not the raw key)
        assertThat(resp.getBody().getMessage()).isEqualTo(ErrorCode.WEAK_PASSWORD.getMessage());
        assertThat(resp.getBody().getMessage()).isEqualTo(resp.getBody().getData().get(0).message());
    }

    @Test
    void handlingRuntimeException_bodyHasNoExceptionClassOrMessage() {
        // FR-10: the catch-all 500 must not leak the exception class or message to the client.
        RuntimeException ex = new RuntimeException("boom secret detail");

        ResponseEntity<ApiResponse<Void>> resp = handler.handlingRuntimeException(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage())
                .isEqualTo(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
        assertThat(resp.getBody().getMessage()).doesNotContain("boom secret detail");
        assertThat(resp.getBody().getMessage()).doesNotContain("RuntimeException");
    }

    @Test
    void handlingBadInput_stillHandles_httpMessageNotReadable() {
        // Sanity: dropping BindException from handlingBadInput didn't break the malformed-body path.
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad json", (HttpInputMessage) null);

        ResponseEntity<ApiResponse<Void>> resp = handler.handlingBadInput(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Invalid or malformed request");
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 02 — Bean-Validation annotation sweep (real validator on constructed DTOs)
    // ---------------------------------------------------------------------------------------------

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    /** True iff some violation targets the given property path. */
    private static <T> boolean violatesProperty(T bean, String propertyPath) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(bean);
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(propertyPath));
    }

    private static String oversize(int len) {
        return "a".repeat(len);
    }

    @Test
    void tournamentRequest_blankName_violates() {
        TournamentRequest req = TournamentRequest.builder().name("   ").build();
        assertThat(violatesProperty(req, "name")).isTrue();
    }

    @Test
    void tournamentRequest_nonPositiveEntryCap_violates() {
        TournamentRequest req = TournamentRequest.builder().name("Cup").entryCap(0).build();
        assertThat(violatesProperty(req, "entryCap")).isTrue();
    }

    @Test
    void tournamentRequest_oversizeLocation_violates() {
        TournamentRequest req = TournamentRequest.builder()
                .name("Cup").location(oversize(256)).build();
        assertThat(violatesProperty(req, "location")).isTrue();
    }

    @Test
    void tournamentRequest_negativeMinAge_violatesNestedEligibility() {
        TournamentRequest req = TournamentRequest.builder()
                .name("Cup")
                .eligibility(EligibilityDto.builder().minAgeYears(-1).build())
                .build();
        // Cascades through @Valid on the eligibility field.
        assertThat(violatesProperty(req, "eligibility.minAgeYears")).isTrue();
    }

    @Test
    void assignParticipantRequest_nonPositiveLaneOrEntry_violates() {
        AssignParticipantRequest req =
                new AssignParticipantRequest(java.util.UUID.randomUUID(), 0, -1);
        assertThat(violatesProperty(req, "laneNo")).isTrue();
        assertThat(violatesProperty(req, "entryNo")).isTrue();
    }

    @Test
    void assignHorseToRaceRequest_nonPositiveLaneOrEntry_violates() {
        AssignHorseToRaceRequest req =
                new AssignHorseToRaceRequest(java.util.UUID.randomUUID(), 0, -1);
        assertThat(violatesProperty(req, "laneNo")).isTrue();
        assertThat(violatesProperty(req, "entryNo")).isTrue();
    }

    @Test
    void registerOwnerRequest_oversizeFullName_violates() {
        RegisterOwnerRequest req = new RegisterOwnerRequest(
                oversize(256), "owner@example.com", "", "Password1!", "Password1!",
                null, null, null, null, true);
        assertThat(violatesProperty(req, "fullName")).isTrue();
    }

    @Test
    void registerSpectatorRequest_oversizeFullName_violates() {
        RegisterSpectatorRequest req = new RegisterSpectatorRequest(
                oversize(256), "spec@example.com", "", "Password1!", "Password1!", true);
        assertThat(violatesProperty(req, "fullName")).isTrue();
    }

    @Test
    void registerJockeyRequest_oversizeFirstAndLastName_violates() {
        RegisterJockeyRequest req = new RegisterJockeyRequest(
                "jockey@example.com", "Password1!", "Password1!",
                oversize(256), oversize(256),
                20, 120.0, "VN", 2, "FLAT", null, null, true);
        assertThat(violatesProperty(req, "firstName")).isTrue();
        assertThat(violatesProperty(req, "lastName")).isTrue();
    }

    @Test
    void rejectEntryRequest_blankReason_violates() {
        RejectEntryRequest req = new RejectEntryRequest();
        req.setReason("   ");
        assertThat(violatesProperty(req, "reason")).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 02 — @Valid presence on flagged @RequestBody params (reflective wiring check)
    // ---------------------------------------------------------------------------------------------

    /** Asserts the (single) @RequestBody parameter of the named method also carries @Valid. */
    private static void assertRequestBodyIsValidated(Class<?> controller, String methodName) {
        Method target = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                target = m;
                break;
            }
        }
        assertThat(target).as("method %s on %s", methodName, controller.getSimpleName()).isNotNull();

        Parameter body = null;
        for (Parameter p : target.getParameters()) {
            if (p.isAnnotationPresent(RequestBody.class)) {
                body = p;
                break;
            }
        }
        assertThat(body).as("@RequestBody parameter of %s", methodName).isNotNull();

        boolean hasValid = false;
        for (Annotation a : body.getAnnotations()) {
            if (a.annotationType().equals(Valid.class)) {
                hasValid = true;
                break;
            }
        }
        assertThat(hasValid).as("@Valid on @RequestBody of %s", methodName).isTrue();
    }

    @Test
    void certify_requestBody_isValidated() {
        assertRequestBodyIsValidated(RaceResultController.class, "certify");
    }

    @Test
    void updateResult_requestBody_isValidated() {
        assertRequestBodyIsValidated(RaceResultController.class, "updateResult");
    }

    @Test
    void rejectEntry_requestBody_isValidated() {
        assertRequestBodyIsValidated(EntryDocumentReviewController.class, "rejectEntry");
    }
}
