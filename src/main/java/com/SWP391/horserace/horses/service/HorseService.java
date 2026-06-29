package com.SWP391.horserace.horses.service;

import com.SWP391.horserace.horses.dto.AssignHorseToRaceRequest;
import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.dto.HorseStatsResponse;
import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.horses.dto.PedigreeResponse;
import com.SWP391.horserace.horses.dto.RaceHistoryItemResponse;
import com.SWP391.horserace.horses.dto.RideIntelligenceResponse;
import com.SWP391.horserace.horses.dto.UpdateMedicalStatusRequest;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface HorseService {

    /**
     * List horses with search + filter + sort + pagination.
     *
     * @param currentUserId the authenticated caller — used to resolve {@code ?ownerUserId=me}
     *                      (the "My Stable" filter); may be null for anonymous/public calls.
     */
    Page<HorseResponse> listHorses(HorseFilterRequest filter, UUID currentUserId);

    HorseResponse getHorseById(UUID horseId);

    /** Create a horse owned by {@code ownerUserId}. */
    HorseResponse createHorse(UUID ownerUserId, HorseRequest request);

    /** Partial update; only the horse's owner (or admin) may update. */
    HorseResponse updateHorse(UUID currentUserId, UUID horseId, HorseRequest request);

    /** Soft-delete; only the horse's owner (or admin) may delete. */
    void deleteHorse(UUID currentUserId, UUID horseId);

    /** Upload/replace the horse photo; only the horse's owner (or admin) may do this. */
    HorseResponse updateHorseImage(UUID currentUserId, UUID horseId, MultipartFile file);

    /** Career statistics of a horse (read-only): earnings, starts, wins, top3, grade, characteristics. */
    HorseStatsResponse getStats(UUID horseId);

    /** Pedigree of a horse (read-only): sire, dam, trainer. */
    PedigreeResponse getPedigree(UUID horseId);

    /** Current medical status of a horse (read-only). */
    MedicalStatusResponse getMedicalStatus(UUID horseId);

    /** Update a horse's medical status (partial); only the owner (or admin) may do this. */
    MedicalStatusResponse updateMedicalStatus(UUID currentUserId, UUID horseId, UpdateMedicalStatusRequest request);

    /** Full race history of a horse, newest first (read-only). */
    List<RaceHistoryItemResponse> getRaceHistory(UUID horseId);

    /** Assign a horse to a race via its approved registration; only the owner (or admin) may do this. */
    RaceEntryResponse assignHorseToRace(UUID currentUserId, UUID horseId, AssignHorseToRaceRequest request);

    /** Open races this horse can still be entered into (APPROVED registration + not already entered). */
    java.util.List<com.SWP391.horserace.horses.dto.EnterableRaceResponse> getEnterableRaces(UUID horseId);

    // ---- Medical records (owner-managed) ----
    java.util.List<com.SWP391.horserace.horses.dto.MedicalRecordResponse> listMedicalRecords(UUID horseId);
    com.SWP391.horserace.horses.dto.MedicalRecordResponse addMedicalRecord(UUID currentUserId, UUID horseId, com.SWP391.horserace.horses.dto.MedicalRecordRequest request);
    com.SWP391.horserace.horses.dto.MedicalRecordResponse updateMedicalRecord(UUID currentUserId, UUID horseId, UUID recordId, com.SWP391.horserace.horses.dto.MedicalRecordRequest request);
    void deleteMedicalRecord(UUID currentUserId, UUID horseId, UUID recordId);

    /** Ride intelligence for a horse (FE-v2 jockey contract #7): surface, post time, trainer, owner, recent form. */
    RideIntelligenceResponse getRideIntelligence(UUID horseId);
}
