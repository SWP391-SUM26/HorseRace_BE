package com.SWP391.horserace.shared.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over file storage so the backend can swap local disk for S3/GCS later
 * without touching callers. Implementations decide where bytes physically live.
 */
public interface FileStorageService {

    /**
     * Store an uploaded file under {@code folder}.
     *
     * @return the storage key (e.g. {@code "avatars/3f2a....png"}) — stable id used by {@link #load}.
     */
    String store(MultipartFile file, String folder);

    /** Load a previously stored file by its key. */
    Resource load(String key);
}
