package com.SWP391.horserace.horses.service;

import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface HorseService {

    /** List horses with search + filter + sort + pagination. */
    Page<HorseResponse> listHorses(HorseFilterRequest filter);

    HorseResponse getHorseById(UUID horseId);

    /** Create a horse owned by {@code ownerUserId}. */
    HorseResponse createHorse(UUID ownerUserId, HorseRequest request);

    /** Partial update; only the horse's owner (or admin) may update. */
    HorseResponse updateHorse(UUID currentUserId, UUID horseId, HorseRequest request);

    /** Soft-delete; only the horse's owner (or admin) may delete. */
    void deleteHorse(UUID currentUserId, UUID horseId);

    /** Upload/replace the horse photo; only the horse's owner (or admin) may do this. */
    HorseResponse updateHorseImage(UUID currentUserId, UUID horseId, MultipartFile file);
}
