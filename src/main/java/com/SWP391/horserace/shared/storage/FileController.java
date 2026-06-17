package com.SWP391.horserace.shared.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves stored files (e.g. avatars, horse photos) via the storage abstraction, so the URL stays
 * the same when storage moves from local disk to S3. Path: {@code GET /api/v1/files/{folder}/{filename}}.
 *
 * <p>Intentionally public (no auth): avatars/horse images are non-sensitive and the keys are
 * unguessable UUIDs. If any private file type is added later, gate it behind auth/ownership here.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String folder, @PathVariable String filename) {
        Resource resource = fileStorageService.load(folder + "/" + filename);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        // Defence-in-depth against stored-XSS: stop MIME sniffing and serve with an explicit
        // disposition so a stored blob cannot execute as script in the app origin.
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}
