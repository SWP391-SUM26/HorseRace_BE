package com.SWP391.horserace.shared.storage;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Local-disk implementation of {@link FileStorageService}. Files are written under
 * {@code app.storage.location} (default {@code ./uploads}). Keys are {@code folder/uuid.ext}.
 * Both store and load are guarded against path traversal, and the stored extension is derived
 * from the MIME type (not the client filename) to prevent stored-XSS via e.g. {@code evil.svg}.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    /** Safe extension per (validated) MIME type — never trust the client-supplied filename. */
    private static final Map<String, String> CONTENT_TYPE_EXTENSION = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif");

    private final Path root;

    public LocalFileStorageService(@Value("${app.storage.location:uploads}") String location) {
        this.root = Paths.get(location).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        String ext = extension(file);
        String filename = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String key = folder + "/" + filename;

        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) { // path-traversal guard
            throw new AppException(ErrorCode.FILE_STORAGE_FAILED);
        }
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public Resource load(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) { // path-traversal guard
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new AppException(ErrorCode.FILE_NOT_FOUND);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    /**
     * Derive a safe extension from the MIME type (the caller validates content type against an
     * allow-list). Falls back to a sanitised filename extension only as defence-in-depth; the raw
     * client filename is never used verbatim, so an attacker cannot upload {@code evil.svg} /
     * {@code evil.html} under an image content type and have it served as script (stored XSS).
     */
    private String extension(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String mapped = CONTENT_TYPE_EXTENSION.get(contentType.toLowerCase());
            if (mapped != null) {
                return mapped;
            }
        }
        String name = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!ext.isEmpty() && ext.length() <= 5) {
                return ext;
            }
        }
        return "bin";
    }
}
