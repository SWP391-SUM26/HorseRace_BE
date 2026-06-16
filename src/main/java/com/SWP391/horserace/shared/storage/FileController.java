package com.SWP391.horserace.shared.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves stored files (e.g. avatars) via the storage abstraction, so the URL stays the same
 * when storage moves from local disk to S3. Path: {@code GET /api/v1/files/{folder}/{filename}}.
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
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
