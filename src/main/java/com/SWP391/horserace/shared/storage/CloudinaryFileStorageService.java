package com.SWP391.horserace.shared.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Cloudinary-backed {@link FileStorageService} for PUBLIC images (avatars, horse, tournament).
 * Registered only when {@code app.storage.provider=cloudinary}, and marked {@link Primary} so the
 * unqualified injection point ({@link ImageUploadService}) picks it up while callers that must stay
 * on local disk (jockey docs, the file server) keep their {@code @Qualifier("localFileStorage")}.
 *
 * <p>Keys are {@code <public_id>.<format>} (e.g. {@code tournaments/uuid.jpg}) so both the delivery
 * URL and the delete handle can be derived without a second round-trip. Credentials come from the
 * {@code CLOUDINARY_URL} env var (or {@code app.cloudinary.url}); nothing secret is committed.
 */
@Service
@Primary
@Slf4j
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
public class CloudinaryFileStorageService implements FileStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryFileStorageService(@Value("${app.cloudinary.url:${CLOUDINARY_URL:}}") String url) {
        // Explicit URL when provided; otherwise fall back to Cloudinary's own CLOUDINARY_URL env read.
        this.cloudinary = (url == null || url.isBlank()) ? new Cloudinary() : new Cloudinary(url);
    }

    @Override
    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", UUID.randomUUID().toString(),
                            "resource_type", "image",
                            "overwrite", true));
            String publicId = (String) result.get("public_id");   // e.g. "tournaments/uuid"
            String format = (String) result.get("format");        // e.g. "jpg"
            return (format == null || format.isBlank()) ? publicId : publicId + "." + format;
        } catch (IOException | RuntimeException e) {
            log.error("Cloudinary upload failed for folder '{}': {}", folder, e.getMessage());
            throw new AppException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public Resource load(String key) {
        // Public images are served directly from the Cloudinary CDN, never streamed through the app.
        throw new AppException(ErrorCode.FILE_NOT_FOUND);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String publicId = stripExtension(key);
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
        } catch (IOException | RuntimeException e) {
            // best-effort: an orphaned CDN asset is preferable to failing the request, but log it.
            log.warn("Cloudinary delete failed for '{}': {}", publicId, e.getMessage());
        }
    }

    @Override
    public String publicUrl(String key) {
        // key already carries the extension, so generate() yields the full secure delivery URL.
        return cloudinary.url().secure(true).generate(key);
    }

    @Override
    public String resolveKey(String publicUrl) {
        if (publicUrl == null) {
            return null;
        }
        String cloudName = cloudinary.config.cloudName;
        if (cloudName == null) {
            return null;
        }
        // Recognise only URLs we produced for this cloud: .../<cloud>/image/upload/[v123/]<key>
        String marker = "/" + cloudName + "/image/upload/";
        int at = publicUrl.indexOf(marker);
        if (at < 0) {
            return null;
        }
        String tail = publicUrl.substring(at + marker.length());
        // Drop any query string (e.g. the "?_a=" analytics token generate() appends).
        int q = tail.indexOf('?');
        if (q >= 0) {
            tail = tail.substring(0, q);
        }
        // Strip an optional version segment (v1/, v1699999999/) that Cloudinary may embed.
        if (tail.matches("^v\\d+/.*")) {
            tail = tail.substring(tail.indexOf('/') + 1);
        }
        return tail.isBlank() ? null : tail;
    }

    private String stripExtension(String key) {
        int dot = key.lastIndexOf('.');
        int slash = key.lastIndexOf('/');
        return (dot > slash && dot > 0) ? key.substring(0, dot) : key;
    }
}
