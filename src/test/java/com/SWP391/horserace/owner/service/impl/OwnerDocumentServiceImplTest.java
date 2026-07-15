package com.SWP391.horserace.owner.service.impl;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
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

    private OwnerDocumentServiceImpl service;

    private final UUID callerId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OwnerDocumentServiceImpl(horseRepository, attachmentService, entryDocumentReviewService);
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
}
