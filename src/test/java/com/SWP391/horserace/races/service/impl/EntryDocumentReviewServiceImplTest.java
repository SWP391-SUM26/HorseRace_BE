package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.attachments.entity.Attachment;
import com.SWP391.horserace.attachments.entity.SensitivityLevel;
import com.SWP391.horserace.attachments.repository.AttachmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.inspections.entity.DocumentReviewStatus;
import com.SWP391.horserace.inspections.entity.EntryDocumentReview;
import com.SWP391.horserace.inspections.repository.EntryDocumentReviewRepository;
import com.SWP391.horserace.races.dto.EntryReviewResponse;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryDocumentReviewServiceImplTest {

    @Mock RaceEntryRepository raceEntryRepository;
    @Mock EntryDocumentReviewRepository reviewRepository;
    @Mock AttachmentRepository attachmentRepository;
    @Mock RefereeAssignmentRepository refereeAssignmentRepository;
    @Mock UserRepository userRepository;

    private EntryDocumentReviewServiceImpl service;

    private final UUID callerId = UUID.randomUUID();
    private final UUID raceId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EntryDocumentReviewServiceImpl(
                raceEntryRepository, reviewRepository, attachmentRepository,
                refereeAssignmentRepository, userRepository);
    }

    // ── fixtures ──

    private User refereeCaller() {
        Role role = Role.builder().roleCode("RACE_REFEREE").build();
        return User.builder().userId(callerId).fullName("Ref E. Ree").role(role).build();
    }

    private RaceEntry entryInRace(UUID rId) {
        User owner = User.builder().userId(ownerId).fullName("Owen Owner").build();
        Horse horse = Horse.builder().horseId(horseId).owner(owner).name("Thunder").build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(UUID.randomUUID()).owner(owner).horse(horse).build();
        Race race = Race.builder().raceId(rId).status(RaceStatus.OPEN).build();
        return RaceEntry.builder().entryId(entryId).entryNo(3).registration(reg).race(race).build();
    }

    /** Caller is a CONFIRMED referee for the race (passes the RT-CRITICAL-4 assignment gate). */
    private void stubConfirmedReferee() {
        when(userRepository.findByUserIdAndDeletedFalse(callerId)).thenReturn(Optional.of(refereeCaller()));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusIn(
                raceId, callerId, RefereeAssignmentStatus.OFFICIATING)).thenReturn(true);
    }

    // ── getEntryReviews ──

    @Test
    void getEntryReviews_returnsEntriesWithOwnerAndHorseDocsAndDefaultPending() {
        stubConfirmedReferee();
        RaceEntry entry = entryInRace(raceId);
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of(entry));
        when(reviewRepository.findByEntry_EntryIdIn(List.of(entryId))).thenReturn(List.of());

        Attachment ownerDoc = Attachment.builder().attachmentId(UUID.randomUUID())
                .ownerEntityType("OWNER").ownerEntityId(ownerId).objectKey("attachments/o.pdf")
                .fileName("owner.pdf").sensitivityLevel(SensitivityLevel.RESTRICTED).build();
        Attachment horseDoc = Attachment.builder().attachmentId(UUID.randomUUID())
                .ownerEntityType("HORSE").ownerEntityId(horseId).objectKey("attachments/h.pdf")
                .fileName("horse.pdf").sensitivityLevel(SensitivityLevel.RESTRICTED).build();
        when(attachmentRepository.findByOwner("OWNER", ownerId)).thenReturn(List.of(ownerDoc));
        when(attachmentRepository.findByOwner("HORSE", horseId)).thenReturn(List.of(horseDoc));

        List<EntryReviewResponse> result = service.getEntryReviews(callerId, raceId);

        assertThat(result).hasSize(1);
        EntryReviewResponse row = result.get(0);
        assertThat(row.getEntryId()).isEqualTo(entryId);
        assertThat(row.getEntryNo()).isEqualTo(3);
        assertThat(row.getHorseName()).isEqualTo("Thunder");
        assertThat(row.getOwnerName()).isEqualTo("Owen Owner");
        assertThat(row.getDocumentStatus()).isEqualTo(DocumentReviewStatus.PENDING);
        assertThat(row.getOwnerDocs()).hasSize(1);
        assertThat(row.getOwnerDocs().get(0).getUrl()).isEqualTo("/api/v1/files/attachments/o.pdf");
        assertThat(row.getHorseDocs()).hasSize(1);
        assertThat(row.getHorseDocs().get(0).getSensitivityLevel()).isEqualTo("RESTRICTED");
    }

    @Test
    void getEntryReviews_refereeNotAssignedToRace_throws() {
        // RT-CRITICAL-4: a referee not CONFIRMED for THIS race may not read its entries.
        when(userRepository.findByUserIdAndDeletedFalse(callerId)).thenReturn(Optional.of(refereeCaller()));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusIn(
                raceId, callerId, RefereeAssignmentStatus.OFFICIATING)).thenReturn(false);

        assertThatThrownBy(() -> service.getEntryReviews(callerId, raceId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_NOT_ASSIGNED);
    }

    // ── acceptEntry ──

    @Test
    void acceptEntry_setsAcceptedWithReviewer() {
        stubConfirmedReferee();
        RaceEntry entry = entryInRace(raceId);
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(reviewRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(EntryDocumentReview.class))).thenAnswer(i -> i.getArgument(0));

        service.acceptEntry(callerId, raceId, entryId);

        ArgumentCaptor<EntryDocumentReview> captor = ArgumentCaptor.forClass(EntryDocumentReview.class);
        verify(reviewRepository).save(captor.capture());
        EntryDocumentReview saved = captor.getValue();
        assertThat(saved.getDocumentStatus()).isEqualTo(DocumentReviewStatus.ACCEPTED);
        assertThat(saved.getReviewedBy()).isNotNull();
        assertThat(saved.getReviewedBy().getUserId()).isEqualTo(callerId);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getEntry().getEntryId()).isEqualTo(entryId);
    }

    @Test
    void acceptEntry_entryNotInRace_throwsMismatch() {
        stubConfirmedReferee();
        RaceEntry entry = entryInRace(UUID.randomUUID()); // belongs to a DIFFERENT race
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.acceptEntry(callerId, raceId, entryId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENTRY_RACE_MISMATCH);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void acceptEntry_refereeNotAssignedToRace_throws() {
        // RT-CRITICAL-4 (write path).
        when(userRepository.findByUserIdAndDeletedFalse(callerId)).thenReturn(Optional.of(refereeCaller()));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusIn(
                raceId, callerId, RefereeAssignmentStatus.OFFICIATING)).thenReturn(false);

        assertThatThrownBy(() -> service.acceptEntry(callerId, raceId, entryId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFEREE_NOT_ASSIGNED);
        verify(raceEntryRepository, never()).findByIdWithDetails(any());
        verify(reviewRepository, never()).save(any());
    }

    // ── rejectEntry ──

    @Test
    void rejectEntry_setsRejectedWithReason() {
        stubConfirmedReferee();
        RaceEntry entry = entryInRace(raceId);
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(reviewRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(EntryDocumentReview.class))).thenAnswer(i -> i.getArgument(0));

        service.rejectEntry(callerId, raceId, entryId, "Passport scan illegible");

        ArgumentCaptor<EntryDocumentReview> captor = ArgumentCaptor.forClass(EntryDocumentReview.class);
        verify(reviewRepository).save(captor.capture());
        EntryDocumentReview saved = captor.getValue();
        assertThat(saved.getDocumentStatus()).isEqualTo(DocumentReviewStatus.REJECTED);
        assertThat(saved.getReviewReason()).isEqualTo("Passport scan illegible");
        assertThat(saved.getReviewedBy().getUserId()).isEqualTo(callerId);
    }

    @Test
    void rejectEntry_blankReason_throws() {
        stubConfirmedReferee();

        assertThatThrownBy(() -> service.rejectEntry(callerId, raceId, entryId, "   "))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENTRY_REVIEW_REASON_REQUIRED);
        verify(reviewRepository, never()).save(any());
    }

    // ── resetToPendingOnUpload (FR-12) ──

    @Test
    void resetToPendingOnUpload_rejectedEntry_becomesPending() {
        RaceEntry entry = entryInRace(raceId); // race OPEN (not final)
        when(raceEntryRepository.findHistoryByHorseId(horseId)).thenReturn(List.of(entry));
        EntryDocumentReview rejected = EntryDocumentReview.builder()
                .reviewId(UUID.randomUUID()).entry(entry)
                .documentStatus(DocumentReviewStatus.REJECTED).reviewReason("bad scan")
                .reviewedBy(refereeCaller()).build();
        when(reviewRepository.findByEntry_EntryIdIn(List.of(entryId))).thenReturn(List.of(rejected));
        when(reviewRepository.save(any(EntryDocumentReview.class))).thenAnswer(i -> i.getArgument(0));

        service.resetToPendingOnUpload(ownerId, horseId);

        ArgumentCaptor<EntryDocumentReview> captor = ArgumentCaptor.forClass(EntryDocumentReview.class);
        verify(reviewRepository).save(captor.capture());
        EntryDocumentReview saved = captor.getValue();
        assertThat(saved.getDocumentStatus()).isEqualTo(DocumentReviewStatus.PENDING);
        assertThat(saved.getReviewReason()).isNull();
        assertThat(saved.getReviewedBy()).isNull();
        assertThat(saved.getReviewedAt()).isNull();
    }

    @Test
    void resetToPendingOnUpload_acceptedEntry_untouched() {
        // An already-ACCEPTED entry must NOT be flipped by a new upload (RT-CRITICAL-5 concern).
        RaceEntry entry = entryInRace(raceId);
        when(raceEntryRepository.findHistoryByHorseId(horseId)).thenReturn(List.of(entry));
        EntryDocumentReview accepted = EntryDocumentReview.builder()
                .reviewId(UUID.randomUUID()).entry(entry)
                .documentStatus(DocumentReviewStatus.ACCEPTED).build();
        when(reviewRepository.findByEntry_EntryIdIn(List.of(entryId))).thenReturn(List.of(accepted));

        service.resetToPendingOnUpload(ownerId, horseId);

        verify(reviewRepository, never()).save(any());
    }
}
