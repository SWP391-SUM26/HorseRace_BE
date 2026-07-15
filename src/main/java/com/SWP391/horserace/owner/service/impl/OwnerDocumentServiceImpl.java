package com.SWP391.horserace.owner.service.impl;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.service.AttachmentService;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.owner.service.OwnerDocumentService;
import com.SWP391.horserace.races.service.EntryDocumentReviewService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerDocumentServiceImpl implements OwnerDocumentService {

    private final HorseRepository horseRepository;
    private final AttachmentService attachmentService;
    private final EntryDocumentReviewService entryDocumentReviewService;

    @Override
    @Transactional
    public AttachmentResponse uploadDocument(UUID callerId, MultipartFile file, String ownerType, UUID horseId) {
        if (callerId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String type = ownerType == null ? null : ownerType.trim().toUpperCase();
        if ("HORSE".equals(type)) {
            if (horseId == null) {
                throw new AppException(ErrorCode.HORSE_NOT_FOUND);
            }
            // RT-CRITICAL-5: the horse must belong to the caller, else this is a cross-owner IDOR.
            Horse horse = horseRepository.findByHorseIdAndDeletedFalse(horseId)
                    .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));
            if (horse.getOwner() == null || !callerId.equals(horse.getOwner().getUserId())) {
                throw new AppException(ErrorCode.NOT_HORSE_OWNER);
            }
            AttachmentResponse resp = attachmentService.uploadOwnerDocument(callerId, file, "HORSE", horseId);
            entryDocumentReviewService.resetToPendingOnUpload(callerId, horseId);
            return resp;
        }
        if ("OWNER".equals(type)) {
            // Force ownerEntityId to the caller — never trust a client-supplied owner id.
            AttachmentResponse resp = attachmentService.uploadOwnerDocument(callerId, file, "OWNER", callerId);
            entryDocumentReviewService.resetToPendingOnUpload(callerId, null);
            return resp;
        }
        throw new AppException(ErrorCode.ATTACHMENT_INVALID_OWNER_TYPE);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<AttachmentResponse> listMyOwnerDocuments(UUID callerId) {
        if (callerId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return attachmentService.listOwnerDocuments("OWNER", callerId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<AttachmentResponse> listHorseDocuments(UUID callerId, UUID horseId) {
        if (callerId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Horse horse = horseRepository.findByHorseIdAndDeletedFalse(horseId)
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));
        if (horse.getOwner() == null || !callerId.equals(horse.getOwner().getUserId())) {
            throw new AppException(ErrorCode.NOT_HORSE_OWNER);
        }
        return attachmentService.listOwnerDocuments("HORSE", horseId);
    }
}
