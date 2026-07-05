package com.SWP391.horserace.attachments.service.impl;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.entity.Attachment;
import com.SWP391.horserace.attachments.entity.SensitivityLevel;
import com.SWP391.horserace.attachments.repository.AttachmentRepository;
import com.SWP391.horserace.attachments.service.AttachmentService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.shared.storage.LocalFileStorageService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    /** Allowed polymorphic owner types for attachments (FE-v2 §6). JOCKEY_PROFILE = sensitive jockey docs. */
    private static final Set<String> ALLOWED_OWNER_TYPES = Set.of(
            "RACE_RESULT", "VIOLATION", "RACE", "TOURNAMENT_REGISTRATION", "JOCKEY_PROFILE");
    private static final String STORAGE_FOLDER = "attachments";

    private final AttachmentRepository attachmentRepository;
    // Jockey docs / evidence are RESTRICTED and auth-gated (4B) — ALWAYS local disk, never public CDN.
    @Qualifier(LocalFileStorageService.BEAN_NAME)
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AttachmentResponse upload(UUID userId, MultipartFile file, String ownerEntityType,
                                     UUID ownerEntityId, String sensitivityLevel) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        String ownerType = normalizeOwnerType(ownerEntityType);
        SensitivityLevel sensitivity = parseSensitivity(sensitivityLevel);

        User uploader = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Store the raw bytes via the storage abstraction (any file type — not image-only).
        String key = fileStorageService.store(file, STORAGE_FOLDER);

        Attachment attachment = Attachment.builder()
                .ownerEntityType(ownerType)
                .ownerEntityId(ownerEntityId)
                .objectKey(key)
                .fileName(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .sensitivityLevel(sensitivity)
                .uploadedBy(uploader)
                .build();

        return toResponse(attachmentRepository.save(attachment));
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentDownload download(UUID attachmentId) {
        Attachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
        if (a.getObjectKey() == null) {
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
        return new AttachmentDownload(
                fileStorageService.load(a.getObjectKey()), a.getFileName(), a.getMimeType());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> listByOwner(String ownerEntityType, UUID ownerEntityId) {
        String ownerType = normalizeOwnerType(ownerEntityType);
        return attachmentRepository.findByOwner(ownerType, ownerEntityId).stream()
                .map(this::toResponse)
                .toList();
    }

    private String normalizeOwnerType(String ownerEntityType) {
        if (ownerEntityType == null || !ALLOWED_OWNER_TYPES.contains(ownerEntityType.trim().toUpperCase())) {
            throw new AppException(ErrorCode.ATTACHMENT_INVALID_OWNER_TYPE);
        }
        return ownerEntityType.trim().toUpperCase();
    }

    private SensitivityLevel parseSensitivity(String sensitivityLevel) {
        if (sensitivityLevel == null || sensitivityLevel.isBlank()) {
            return null;
        }
        try {
            return SensitivityLevel.valueOf(sensitivityLevel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.ATTACHMENT_INVALID_SENSITIVITY);
        }
    }

    private AttachmentResponse toResponse(Attachment a) {
        return AttachmentResponse.builder()
                .attachmentId(a.getAttachmentId())
                .ownerEntityType(a.getOwnerEntityType())
                .ownerEntityId(a.getOwnerEntityId())
                .fileName(a.getFileName())
                .mimeType(a.getMimeType())
                .fileSize(a.getFileSize())
                .url(a.getObjectKey() != null ? ImageUploadService.PUBLIC_URL_PREFIX + a.getObjectKey() : null)
                .sensitivityLevel(a.getSensitivityLevel() != null ? a.getSensitivityLevel().name() : null)
                .uploadedAt(a.getUploadedAt())
                .build();
    }
}
