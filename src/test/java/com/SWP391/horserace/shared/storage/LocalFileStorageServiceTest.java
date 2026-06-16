package com.SWP391.horserace.shared.storage;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path tmp;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageService(tmp.toString());
    }

    @Test
    void store_thenLoad_roundTrips() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1, 2, 3, 4});

        String key = storage.store(file, "avatars");

        assertThat(key).startsWith("avatars/").endsWith(".png");
        Resource loaded = storage.load(key);
        assertThat(loaded.exists()).isTrue();
        assertThat(loaded.isReadable()).isTrue();
    }

    @Test
    void store_generatesUniqueKeysPerUpload() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        assertThat(storage.store(file, "avatars")).isNotEqualTo(storage.store(file, "avatars"));
    }

    @Test
    void store_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> storage.store(empty, "avatars"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_EMPTY);
    }

    @Test
    void load_missingKey_throwsNotFound() {
        assertThatThrownBy(() -> storage.load("avatars/does-not-exist.png"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void load_pathTraversal_isBlocked() {
        assertThatThrownBy(() -> storage.load("../../../../etc/passwd"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
    }
}
