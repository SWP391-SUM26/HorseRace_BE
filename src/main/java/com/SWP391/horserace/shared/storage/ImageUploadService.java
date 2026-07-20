package com.SWP391.horserace.shared.storage;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Single home for image-upload rules (allowed MIME types, size limit) so avatar and horse-image
 * uploads can't drift apart. Wraps {@link FileStorageService} for the actual bytes and exposes a
 * best-effort delete used to clean up the previous file when an image is replaced.
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    /** @deprecated moved to {@link LocalFileStorageService#PUBLIC_URL_PREFIX}; kept for callers. */
    @Deprecated
    public static final String PUBLIC_URL_PREFIX = LocalFileStorageService.PUBLIC_URL_PREFIX;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB

    private final FileStorageService fileStorageService;

    /** Validate (non-empty, ≤5MB, allowed image type) then store; returns the public URL. */
    public String storeImageAsUrl(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
        // The active storage impl decides the URL scheme (local /api/v1/files/… vs Cloudinary CDN).
        return fileStorageService.publicUrl(fileStorageService.store(file, folder));
    }

    /** Best-effort delete of a previously stored public URL (no-op for null/external/legacy URLs). */
    public void deleteByUrl(String publicUrl) {
        String key = fileStorageService.resolveKey(publicUrl);
        if (key != null) {
            fileStorageService.delete(key);
        }
    }
}
