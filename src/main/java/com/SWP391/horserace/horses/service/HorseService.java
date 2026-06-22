package com.SWP391.horserace.horses.service;

import com.SWP391.horserace.horses.dto.AssignHorseToRaceRequest;
import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.horses.dto.RaceHistoryItemResponse;
import com.SWP391.horserace.horses.dto.UpdateMedicalStatusRequest;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    /** Current medical status of a horse (read-only). */
    MedicalStatusResponse getMedicalStatus(UUID horseId);

    /** Update a horse's medical status (partial); only the owner (or admin) may do this. */
    MedicalStatusResponse updateMedicalStatus(UUID currentUserId, UUID horseId, UpdateMedicalStatusRequest request);

    /** Full race history of a horse, newest first (read-only). */
    List<RaceHistoryItemResponse> getRaceHistory(UUID horseId);

    /** Assign a horse to a race via its approved registration; only the owner (or admin) may do this. */
    RaceEntryResponse assignHorseToRace(UUID currentUserId, UUID horseId, AssignHorseToRaceRequest request);
}
