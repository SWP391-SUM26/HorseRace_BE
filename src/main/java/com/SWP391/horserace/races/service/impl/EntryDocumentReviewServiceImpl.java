package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.entity.Attachment;
import com.SWP391.horserace.attachments.repository.AttachmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.inspections.entity.DocumentReviewStatus;
import com.SWP391.horserace.inspections.entity.EntryDocumentReview;
import com.SWP391.horserace.inspections.repository.EntryDocumentReviewRepository;
import com.SWP391.horserace.races.dto.EntryReviewResponse;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.service.EntryDocumentReviewService;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.LocalFileStorageService;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntryDocumentReviewServiceImpl implements EntryDocumentReviewService {

    private final RaceEntryRepository raceEntryRepository;
    private final EntryDocumentReviewRepository reviewRepository;
    private final AttachmentRepository attachmentRepository;
    private final RefereeAssignmentRepository refereeAssignmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EntryReviewResponse> getEntryReviews(UUID callerId, UUID raceId) {
        requireAdminOrConfirmedReferee(callerId, raceId);

        List<RaceEntry> entries = raceEntryRepository.findByRaceIdWithHorse(raceId);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
        Map<UUID, EntryDocumentReview> reviewsByEntry = reviewRepository.findByEntry_EntryIdIn(entryIds).stream()
                .collect(Collectors.toMap(r -> r.getEntry().getEntryId(), r -> r));

        return entries.stream()
                .map(entry -> toResponse(entry, reviewsByEntry.get(entry.getEntryId())))
                .toList();
    }

    @Override
    @Transactional
    public EntryReviewResponse acceptEntry(UUID callerId, UUID raceId, UUID entryId) {
        requireAdminOrConfirmedReferee(callerId, raceId);
        RaceEntry entry = loadEntryInRace(raceId, entryId);
        User reviewer = loadUser(callerId);

        EntryDocumentReview review = upsert(entry);
        review.setDocumentStatus(DocumentReviewStatus.ACCEPTED);
        review.setReviewReason(null);
        review.setReviewedBy(reviewer);
        review.setReviewedAt(OffsetDateTime.now());

        return toResponse(entry, reviewRepository.save(review));
    }

    @Override
    @Transactional
    public EntryReviewResponse rejectEntry(UUID callerId, UUID raceId, UUID entryId, String reason) {
        requireAdminOrConfirmedReferee(callerId, raceId);
        if (reason == null || reason.isBlank()) {
            throw new AppException(ErrorCode.ENTRY_REVIEW_REASON_REQUIRED);
        }
        RaceEntry entry = loadEntryInRace(raceId, entryId);
        User reviewer = loadUser(callerId);

        EntryDocumentReview review = upsert(entry);
        review.setDocumentStatus(DocumentReviewStatus.REJECTED);
        review.setReviewReason(reason.trim());
        review.setReviewedBy(reviewer);
        review.setReviewedAt(OffsetDateTime.now());

        return toResponse(entry, reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void resetToPendingOnUpload(UUID ownerUserId, UUID horseId) {
        // Gather the affected entries: the specific horse's entries on a HORSE upload, otherwise all of
        // the owner's entries on an OWNER upload.
        List<RaceEntry> entries = horseId != null
                ? raceEntryRepository.findHistoryByHorseId(horseId)
                : (ownerUserId != null ? raceEntryRepository.findByOwnerUserId(ownerUserId) : List.of());
        if (entries.isEmpty()) {
            return;
        }
        // Only entries whose race is not yet final (past races keep their record).
        List<UUID> entryIds = entries.stream()
                .filter(e -> e.getRace() != null && !isFinal(e.getRace().getStatus()))
                .map(RaceEntry::getEntryId)
                .toList();
        if (entryIds.isEmpty()) {
            return;
        }
        for (EntryDocumentReview review : reviewRepository.findByEntry_EntryIdIn(entryIds)) {
            // A new upload answers a rejection → back to PENDING for re-review. Never un-accept.
            if (review.getDocumentStatus() == DocumentReviewStatus.REJECTED) {
                review.setDocumentStatus(DocumentReviewStatus.PENDING);
                review.setReviewReason(null);
                review.setReviewedBy(null);
                review.setReviewedAt(null);
                reviewRepository.save(review);
            }
        }
    }

    // ── helpers ──

    private void requireAdminOrConfirmedReferee(UUID callerId, UUID raceId) {
        if (callerId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        User caller = loadUser(callerId);
        boolean isAdmin = caller.getRole() != null && "ADMIN".equals(caller.getRole().getRoleCode());
        if (isAdmin) {
            return;
        }
        // RT-CRITICAL-4: a referee may only review races they are CONFIRMED to officiate (IDOR guard).
        if (!refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatus(
                raceId, callerId, RefereeAssignmentStatus.CONFIRMED)) {
            throw new AppException(ErrorCode.REFEREE_NOT_ASSIGNED);
        }
    }

    private User loadUser(UUID userId) {
        return userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private RaceEntry loadEntryInRace(UUID raceId, UUID entryId) {
        RaceEntry entry = raceEntryRepository.findByIdWithDetails(entryId)
                .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));
        if (entry.getRace() == null || !raceId.equals(entry.getRace().getRaceId())) {
            throw new AppException(ErrorCode.ENTRY_RACE_MISMATCH);
        }
        return entry;
    }

    private EntryDocumentReview upsert(RaceEntry entry) {
        return reviewRepository.findByEntry_EntryId(entry.getEntryId())
                .orElseGet(() -> EntryDocumentReview.builder()
                        .entry(entry)
                        .race(entry.getRace())
                        .documentStatus(DocumentReviewStatus.PENDING)
                        .build());
    }

    private boolean isFinal(RaceStatus status) {
        return status == RaceStatus.FINISHED || status == RaceStatus.OFFICIAL || status == RaceStatus.CANCELLED;
    }

    private EntryReviewResponse toResponse(RaceEntry entry, EntryDocumentReview review) {
        TournamentRegistration reg = entry.getRegistration();
        Horse horse = reg != null ? reg.getHorse() : null;
        User owner = reg != null ? reg.getOwner() : null;

        List<AttachmentResponse> ownerDocs = owner != null
                ? mapDocs(attachmentRepository.findByOwner("OWNER", owner.getUserId()))
                : List.of();
        List<AttachmentResponse> horseDocs = horse != null
                ? mapDocs(attachmentRepository.findByOwner("HORSE", horse.getHorseId()))
                : List.of();

        DocumentReviewStatus status = review != null ? review.getDocumentStatus() : DocumentReviewStatus.PENDING;
        User reviewedBy = review != null ? review.getReviewedBy() : null;

        return EntryReviewResponse.builder()
                .entryId(entry.getEntryId())
                .entryNo(entry.getEntryNo())
                .horseName(horse != null ? horse.getName() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .documentStatus(status)
                .reviewReason(review != null ? review.getReviewReason() : null)
                .reviewedByName(reviewedBy != null ? reviewedBy.getFullName() : null)
                .reviewedAt(review != null ? review.getReviewedAt() : null)
                .ownerDocs(ownerDocs)
                .horseDocs(horseDocs)
                .build();
    }

    private List<AttachmentResponse> mapDocs(List<Attachment> attachments) {
        List<AttachmentResponse> out = new ArrayList<>();
        for (Attachment a : attachments) {
            out.add(AttachmentResponse.builder()
                    .attachmentId(a.getAttachmentId())
                    .ownerEntityType(a.getOwnerEntityType())
                    .ownerEntityId(a.getOwnerEntityId())
                    .fileName(a.getFileName())
                    .mimeType(a.getMimeType())
                    .fileSize(a.getFileSize())
                    .url(a.getObjectKey() != null ? LocalFileStorageService.PUBLIC_URL_PREFIX + a.getObjectKey() : null)
                    .sensitivityLevel(a.getSensitivityLevel() != null ? a.getSensitivityLevel().name() : null)
                    .uploadedAt(a.getUploadedAt())
                    .build());
        }
        return out;
    }
}
