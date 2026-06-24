package com.SWP391.horserace.attachments.service.impl;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.entity.Attachment;
import com.SWP391.horserace.attachments.entity.SensitivityLevel;
import com.SWP391.horserace.attachments.repository.AttachmentRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @Mock AttachmentRepository attachmentRepository;
    @Mock FileStorageService fileStorageService;
    @Mock UserRepository userRepository;

    private AttachmentServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttachmentServiceImpl(attachmentRepository, fileStorageService, userRepository);
    }

    private MultipartFile file() {
        return new MockMultipartFile(
                "file", "photofinish-r7.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
    }

    // ── upload ──

    @Test
    void upload_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.upload(null, file(), "RACE_RESULT", ownerId, "INTERNAL"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void upload_emptyFile_fileEmpty() {
        MultipartFile empty = new MockMultipartFile("file", "e.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.upload(userId, empty, "RACE_RESULT", ownerId, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_EMPTY);
    }

    @Test
    void upload_invalidOwnerType_rejected() {
        assertThatThrownBy(() -> service.upload(userId, file(), "BOGUS", ownerId, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID_OWNER_TYPE);
    }

    @Test
    void upload_invalidSensitivity_rejected() {
        // Sensitivity is validated before the user lookup, so no user stub is needed.
        assertThatThrownBy(() -> service.upload(userId, file(), "RACE_RESULT", ownerId, "TOP_SECRET"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID_SENSITIVITY);
    }

    @Test
    void upload_storesFile_persistsRow_mapsResponse() {
        User uploader = User.builder().userId(userId).fullName("Steward A. Khan").build();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(uploader));
        when(fileStorageService.store(any(), eq("attachments"))).thenReturn("attachments/abc.jpg");
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setAttachmentId(UUID.randomUUID());
            a.setUploadedAt(OffsetDateTime.now());
            return a;
        });

        AttachmentResponse resp = service.upload(userId, file(), "RACE_RESULT", ownerId, "INTERNAL");

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        org.mockito.Mockito.verify(attachmentRepository).save(captor.capture());
        Attachment saved = captor.getValue();
        assertThat(saved.getObjectKey()).isEqualTo("attachments/abc.jpg");
        assertThat(saved.getOwnerEntityType()).isEqualTo("RACE_RESULT");
        assertThat(saved.getOwnerEntityId()).isEqualTo(ownerId);
        assertThat(saved.getFileName()).isEqualTo("photofinish-r7.jpg");
        assertThat(saved.getMimeType()).isEqualTo("image/jpeg");
        assertThat(saved.getFileSize()).isEqualTo(4L);
        assertThat(saved.getSensitivityLevel()).isEqualTo(SensitivityLevel.INTERNAL);
        assertThat(saved.getUploadedBy()).isEqualTo(uploader);

        assertThat(resp.getAttachmentId()).isNotNull();
        assertThat(resp.getOwnerEntityType()).isEqualTo("RACE_RESULT");
        assertThat(resp.getUrl()).isEqualTo("/api/v1/files/attachments/abc.jpg");
        assertThat(resp.getSensitivityLevel()).isEqualTo("INTERNAL");
        assertThat(resp.getFileSize()).isEqualTo(4L);
    }

    @Test
    void upload_nullSensitivity_storedAsNull() {
        User uploader = User.builder().userId(userId).fullName("Steward").build();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(uploader));
        when(fileStorageService.store(any(), eq("attachments"))).thenReturn("attachments/x.jpg");
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

        AttachmentResponse resp = service.upload(userId, file(), "VIOLATION", ownerId, null);

        assertThat(resp.getSensitivityLevel()).isNull();
        assertThat(resp.getOwnerEntityType()).isEqualTo("VIOLATION");
    }

    // ── list ──

    @Test
    void list_byOwner_mapsResponses() {
        Attachment a = Attachment.builder()
                .attachmentId(UUID.randomUUID())
                .ownerEntityType("RACE")
                .ownerEntityId(ownerId)
                .objectKey("attachments/clip.mp4")
                .fileName("clip.mp4")
                .mimeType("video/mp4")
                .fileSize(123L)
                .sensitivityLevel(SensitivityLevel.PUBLIC)
                .uploadedAt(OffsetDateTime.now())
                .build();
        when(attachmentRepository.findByOwner("RACE", ownerId)).thenReturn(List.of(a));

        List<AttachmentResponse> result = service.listByOwner("RACE", ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUrl()).isEqualTo("/api/v1/files/attachments/clip.mp4");
        assertThat(result.get(0).getSensitivityLevel()).isEqualTo("PUBLIC");
        assertThat(result.get(0).getOwnerEntityType()).isEqualTo("RACE");
    }

    @Test
    void list_invalidOwnerType_rejected() {
        assertThatThrownBy(() -> service.listByOwner("BOGUS", ownerId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID_OWNER_TYPE);
    }
}
