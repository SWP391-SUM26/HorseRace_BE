package com.SWP391.horserace.owner.service.impl;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.entity.Attachment;
import com.SWP391.horserace.attachments.repository.AttachmentRepository;
import com.SWP391.horserace.attachments.service.AttachmentService;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.service.EntryDocumentReviewService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerDocumentServiceImplTest {

    @Mock HorseRepository horseRepository;
    @Mock AttachmentService attachmentService;
    @Mock EntryDocumentReviewService entryDocumentReviewService;
    @Mock AttachmentRepository attachmentRepository;

    private OwnerDocumentServiceImpl service;

    private final UUID callerId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OwnerDocumentServiceImpl(
                horseRepository, attachmentService, entryDocumentReviewService, attachmentRepository);
    }

    private MultipartFile file() {
        return new MockMultipartFile("file", "papers.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    @Test
    void uploadHorseDocument_notOwnerOfHorse_throwsForbidden() {
        // RT-CRITICAL-5: horse belongs to a DIFFERENT owner → forbidden, no upload, no reset.
        User otherOwner = User.builder().userId(UUID.randomUUID()).build();
        Horse horse = Horse.builder().horseId(horseId).owner(otherOwner).name("NotMine").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> service.uploadDocument(callerId, file(), "HORSE", horseId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_HORSE_OWNER);

        verify(attachmentService, never()).uploadOwnerDocument(any(), any(), any(), any());
        verify(entryDocumentReviewService, never()).resetToPendingOnUpload(any(), any());
    }

    @Test
    void uploadHorseDocument_owner_uploadsAndResets() {
        User owner = User.builder().userId(callerId).build();
        Horse horse = Horse.builder().horseId(horseId).owner(owner).name("Mine").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(attachmentService.uploadOwnerDocument(eq(callerId), any(), eq("HORSE"), eq(horseId)))
                .thenReturn(AttachmentResponse.builder().attachmentId(UUID.randomUUID()).build());

        service.uploadDocument(callerId, file(), "HORSE", horseId);

        verify(attachmentService).uploadOwnerDocument(eq(callerId), any(), eq("HORSE"), eq(horseId));
        verify(entryDocumentReviewService).resetToPendingOnUpload(callerId, horseId);
    }

    @Test
    void uploadOwnerDocument_forcesOwnerIdToCallerAndResets() {
        when(attachmentService.uploadOwnerDocument(eq(callerId), any(), eq("OWNER"), eq(callerId)))
                .thenReturn(AttachmentResponse.builder().attachmentId(UUID.randomUUID()).build());

        service.uploadDocument(callerId, file(), "OWNER", null);

        // ownerEntityId is forced to the caller; reset uses null horseId (owner-scoped).
        verify(attachmentService).uploadOwnerDocument(eq(callerId), any(), eq("OWNER"), eq(callerId));
        verify(entryDocumentReviewService).resetToPendingOnUpload(callerId, null);
        verify(horseRepository, never()).findByHorseIdAndDeletedFalse(any());
    }

    @Test
    void uploadDocument_invalidType_throws() {
        assertThatThrownBy(() -> service.uploadDocument(callerId, file(), "RACE", null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_INVALID_OWNER_TYPE);
    }

    @Test
    void uploadHorseDocument_returnedResponse_isFromAttachmentService() {
        User owner = User.builder().userId(callerId).build();
        Horse horse = Horse.builder().horseId(horseId).owner(owner).name("Mine").build();
        UUID attachmentId = UUID.randomUUID();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(attachmentService.uploadOwnerDocument(any(), any(), eq("HORSE"), eq(horseId)))
                .thenReturn(AttachmentResponse.builder().attachmentId(attachmentId).build());

        AttachmentResponse resp = service.uploadDocument(callerId, file(), "HORSE", horseId);

        assertThat(resp.getAttachmentId()).isEqualTo(attachmentId);
    }

    @Test
    void listHorseDocuments_notOwner_throwsForbidden() {
        // HIGH fix: listing a horse's docs is ownership-checked too, not just uploads.
        User otherOwner = User.builder().userId(UUID.randomUUID()).build();
        Horse horse = Horse.builder().horseId(horseId).owner(otherOwner).name("NotMine").build();
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> service.listHorseDocuments(callerId, horseId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_HORSE_OWNER);
        verify(attachmentService, never()).listOwnerDocuments(any(), any());
    }

    @Test
    void listMyOwnerDocuments_delegatesToOwnerScopedList() {
        when(attachmentService.listOwnerDocuments("OWNER", callerId)).thenReturn(java.util.List.of());

        service.listMyOwnerDocuments(callerId);

        verify(attachmentService).listOwnerDocuments("OWNER", callerId);
    }

    // ── download own OWNER/HORSE document (Feature #12) ──

    private AttachmentService.AttachmentDownload download() {
        return new AttachmentService.AttachmentDownload(null, "papers.pdf", "application/pdf");
    }

    @Test
    void downloadOwnerDocument_ownOwnerDoc_succeeds() {
        UUID aid = UUID.randomUUID();
        Attachment att = Attachment.builder()
                .attachmentId(aid).ownerEntityType("OWNER").ownerEntityId(callerId).build();
        when(attachmentRepository.findById(aid)).thenReturn(Optional.of(att));
        when(attachmentService.download(aid)).thenReturn(download());

        AttachmentService.AttachmentDownload d = service.downloadOwnerDocument(callerId, aid);

        assertThat(d.fileName()).isEqualTo("papers.pdf");
    }

    @Test
    void downloadOwnerDocument_ownHorseDoc_succeeds() {
        UUID aid = UUID.randomUUID();
        Attachment att = Attachment.builder()
                .attachmentId(aid).ownerEntityType("HORSE").ownerEntityId(horseId).build();
        Horse horse = Horse.builder().horseId(horseId)
                .owner(User.builder().userId(callerId).build()).name("Mine").build();
        when(attachmentRepository.findById(aid)).thenReturn(Optional.of(att));
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));
        when(attachmentService.download(aid)).thenReturn(download());

        AttachmentService.AttachmentDownload d = service.downloadOwnerDocument(callerId, aid);

        assertThat(d.mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void downloadOwnerDocument_foreignOwnerDoc_throwsFileNotFound() {
        UUID aid = UUID.randomUUID();
        Attachment att = Attachment.builder()
                .attachmentId(aid).ownerEntityType("OWNER").ownerEntityId(UUID.randomUUID()).build();
        when(attachmentRepository.findById(aid)).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> service.downloadOwnerDocument(callerId, aid))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        verify(attachmentService, never()).download(any());
    }

    @Test
    void downloadOwnerDocument_foreignHorseDoc_throwsFileNotFound() {
        UUID aid = UUID.randomUUID();
        Attachment att = Attachment.builder()
                .attachmentId(aid).ownerEntityType("HORSE").ownerEntityId(horseId).build();
        Horse horse = Horse.builder().horseId(horseId)
                .owner(User.builder().userId(UUID.randomUUID()).build()).name("NotMine").build();
        when(attachmentRepository.findById(aid)).thenReturn(Optional.of(att));
        when(horseRepository.findByHorseIdAndDeletedFalse(horseId)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> service.downloadOwnerDocument(callerId, aid))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        verify(attachmentService, never()).download(any());
    }

    @Test
    void downloadOwnerDocument_wrongType_throwsFileNotFound() {
        UUID aid = UUID.randomUUID();
        Attachment att = Attachment.builder()
                .attachmentId(aid).ownerEntityType("RACE_RESULT").ownerEntityId(UUID.randomUUID()).build();
        when(attachmentRepository.findById(aid)).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> service.downloadOwnerDocument(callerId, aid))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void downloadOwnerDocument_missing_throwsFileNotFound() {
        UUID aid = UUID.randomUUID();
        when(attachmentRepository.findById(aid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadOwnerDocument(callerId, aid))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
    }
}
