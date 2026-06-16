package com.SWP391.horserace.horses.service.impl;

import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.horses.repository.HorseSpecification;
import com.SWP391.horserace.horses.service.HorseService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HorseServiceImpl implements HorseService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5MB

    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public Page<HorseResponse> listHorses(HorseFilterRequest filter) {
        return horseRepository
                .findAll(HorseSpecification.withFilters(filter), buildPageable(filter))
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public HorseResponse getHorseById(UUID horseId) {
        return mapToResponse(loadHorse(horseId));
    }

    @Override
    @Transactional
    public HorseResponse createHorse(UUID ownerUserId, HorseRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new AppException(ErrorCode.HORSE_NAME_REQUIRED);
        }
        User owner = userRepository.findByUserIdAndDeletedFalse(ownerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String microchip = trimToNull(request.microchipNo());
        if (microchip != null && horseRepository.existsByMicrochipNoAndDeletedFalse(microchip)) {
            throw new AppException(ErrorCode.MICROCHIP_EXISTED);
        }

        Horse horse = Horse.builder()
                .owner(owner)
                .horseCode(generateHorseCode())
                .name(request.name().trim())
                .microchipNo(microchip)
                .gender(request.gender())
                .breed(trimToNull(request.breed()))
                .color(trimToNull(request.color()))
                .dateOfBirth(request.dateOfBirth())
                .weight(request.weight())
                .originCountry(trimToNull(request.originCountry()))
                .healthStatus(request.healthStatus())
                .registrationStatus(trimToNull(request.registrationStatus()))
                .status(request.status() != null ? request.status() : HorseStatus.ACTIVE)
                .build();

        return mapToResponse(horseRepository.save(horse));
    }

    @Override
    @Transactional
    public HorseResponse updateHorse(UUID currentUserId, UUID horseId, HorseRequest request) {
        Horse horse = loadOwnedHorse(currentUserId, horseId);

        if (request.name() != null && !request.name().isBlank()) {
            horse.setName(request.name().trim());
        }
        if (request.microchipNo() != null) {
            String mc = trimToNull(request.microchipNo());
            if (mc != null && !mc.equals(horse.getMicrochipNo())
                    && horseRepository.existsByMicrochipNoAndDeletedFalse(mc)) {
                throw new AppException(ErrorCode.MICROCHIP_EXISTED);
            }
            horse.setMicrochipNo(mc);
        }
        if (request.gender() != null) horse.setGender(request.gender());
        if (request.breed() != null) horse.setBreed(trimToNull(request.breed()));
        if (request.color() != null) horse.setColor(trimToNull(request.color()));
        if (request.dateOfBirth() != null) horse.setDateOfBirth(request.dateOfBirth());
        if (request.weight() != null) horse.setWeight(request.weight());
        if (request.originCountry() != null) horse.setOriginCountry(trimToNull(request.originCountry()));
        if (request.healthStatus() != null) horse.setHealthStatus(request.healthStatus());
        if (request.registrationStatus() != null) horse.setRegistrationStatus(trimToNull(request.registrationStatus()));
        if (request.status() != null) horse.setStatus(request.status());

        return mapToResponse(horseRepository.save(horse));
    }

    @Override
    @Transactional
    public void deleteHorse(UUID currentUserId, UUID horseId) {
        Horse horse = loadOwnedHorse(currentUserId, horseId);
        horse.setDeleted(true);
        horse.setDeletedAt(OffsetDateTime.now());
        horseRepository.save(horse);
    }

    @Override
    @Transactional
    public HorseResponse updateHorseImage(UUID currentUserId, UUID horseId, MultipartFile file) {
        Horse horse = loadOwnedHorse(currentUserId, horseId);

        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }

        String key = fileStorageService.store(file, "horses");
        horse.setImageUrl("/api/v1/files/" + key);
        return mapToResponse(horseRepository.save(horse));
    }

    // ── helpers ──

    private Horse loadHorse(UUID horseId) {
        return horseRepository.findByHorseIdAndDeletedFalse(horseId)
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));
    }

    private Horse loadOwnedHorse(UUID currentUserId, UUID horseId) {
        Horse horse = loadHorse(horseId);
        if (horse.getOwner() == null || !horse.getOwner().getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_HORSE_OWNER);
        }
        return horse;
    }

    /** Sequential code HRSnnnn, skipping any already taken. */
    private String generateHorseCode() {
        long n = horseRepository.count() + 1;
        String code;
        do {
            code = String.format("HRS%04d", n++);
        } while (horseRepository.existsByHorseCode(code));
        return code;
    }

    private Pageable buildPageable(HorseFilterRequest f) {
        int page = (f.getPage() != null && f.getPage() >= 0) ? f.getPage() : 0;
        int size = (f.getSize() != null && f.getSize() > 0) ? f.getSize() : 10;
        String field = switch (f.getSortBy() != null ? f.getSortBy().trim().toLowerCase() : "createdat") {
            case "name" -> "name";
            case "horsecode", "code" -> "horseCode";
            case "dateofbirth", "dob" -> "dateOfBirth";
            case "status" -> "status";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(f.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private HorseResponse mapToResponse(Horse h) {
        User owner = h.getOwner();
        return HorseResponse.builder()
                .horseId(h.getHorseId())
                .horseCode(h.getHorseCode())
                .ownerUserId(owner != null ? owner.getUserId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .name(h.getName())
                .microchipNo(h.getMicrochipNo())
                .gender(h.getGender())
                .breed(h.getBreed())
                .color(h.getColor())
                .dateOfBirth(h.getDateOfBirth())
                .weight(h.getWeight())
                .originCountry(h.getOriginCountry())
                .healthStatus(h.getHealthStatus())
                .registrationStatus(h.getRegistrationStatus())
                .status(h.getStatus())
                .imageUrl(h.getImageUrl())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
