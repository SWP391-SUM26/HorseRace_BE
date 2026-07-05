package com.SWP391.horserace.shared.storage;

import com.SWP391.horserace.attachments.service.impl.AttachmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the security-critical storage invariant: RESTRICTED files (jockey docs, violation evidence)
 * and the local file server must ALWAYS bind to local disk, even when {@link CloudinaryFileStorageService}
 * is {@code @Primary} (provider=cloudinary). The guarantee rests entirely on {@code @Qualifier} being
 * present on their {@link FileStorageService} constructor parameter (copied by Lombok from the field).
 * If a refactor drops the qualifier, reorders fields, or a Lombok regression stops copying it, these
 * assertions fail loudly instead of silently routing private documents to the public CDN.
 */
class StorageWiringTest {

    @Test
    void attachmentService_isPinnedToLocalStorage() {
        assertFileStorageParamQualifiedLocal(AttachmentServiceImpl.class);
    }

    @Test
    void fileController_isPinnedToLocalStorage() {
        assertFileStorageParamQualifiedLocal(FileController.class);
    }

    @Test
    void beanNameConstantMatchesQualifierValue() {
        assertThat(LocalFileStorageService.BEAN_NAME).isEqualTo("localFileStorage");
    }

    private void assertFileStorageParamQualifiedLocal(Class<?> type) {
        Parameter param = findFileStorageParam(type);
        assertThat(param)
                .as("%s must have a FileStorageService constructor parameter", type.getSimpleName())
                .isNotNull();
        Qualifier qualifier = param.getAnnotation(Qualifier.class);
        assertThat(qualifier)
                .as("%s's FileStorageService must be @Qualifier-pinned so Cloudinary can't be injected",
                        type.getSimpleName())
                .isNotNull();
        assertThat(qualifier.value()).isEqualTo(LocalFileStorageService.BEAN_NAME);
    }

    private Parameter findFileStorageParam(Class<?> type) {
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            for (Parameter p : ctor.getParameters()) {
                if (p.getType().equals(FileStorageService.class)) {
                    return p;
                }
            }
        }
        return null;
    }
}
