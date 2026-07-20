package com.SWP391.horserace.shared.storage;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure URL/key parsing tests for the Cloudinary storage impl. Uses a fixed (fake) CLOUDINARY_URL so
 * {@code cloudinary.url().generate()} and config parsing run fully offline — no network, no real account.
 */
class CloudinaryFileStorageServiceTest {

    private final CloudinaryFileStorageService service =
            new CloudinaryFileStorageService("cloudinary://key:secret@democloud");

    @Test
    void publicUrl_generatesSecureCdnUrlForThisCloud() {
        String url = service.publicUrl("tournaments/abc.jpg");
        assertThat(url).startsWith("https://").contains("democloud").contains("tournaments/abc.jpg");
    }

    @Test
    void resolveKey_roundTripsWithPublicUrl() {
        String key = "tournaments/abc.jpg";
        assertThat(service.resolveKey(service.publicUrl(key))).isEqualTo(key);
    }

    @Test
    void resolveKey_stripsVersionSegment() {
        String url = "https://res.cloudinary.com/democloud/image/upload/v1699999999/tournaments/abc.jpg";
        assertThat(service.resolveKey(url)).isEqualTo("tournaments/abc.jpg");
    }

    @Test
    void resolveKey_withoutVersionSegment() {
        String url = "https://res.cloudinary.com/democloud/image/upload/tournaments/abc.jpg";
        assertThat(service.resolveKey(url)).isEqualTo("tournaments/abc.jpg");
    }

    @Test
    void resolveKey_foreignCloud_returnsNull() {
        String url = "https://res.cloudinary.com/othercloud/image/upload/tournaments/abc.jpg";
        assertThat(service.resolveKey(url)).isNull();
    }

    @Test
    void resolveKey_nonCloudinaryUrl_returnsNull() {
        assertThat(service.resolveKey("/api/v1/files/avatars/abc.png")).isNull();
        assertThat(service.resolveKey(null)).isNull();
    }

    @Test
    void load_isUnsupportedForCdnServedImages() {
        assertThatThrownBy(() -> service.load("tournaments/abc.jpg"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
    }
}
