package com.SWP391.horserace.horses.service.impl;

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
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseStatus;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.horses.repository.HorseSpecification;
import com.SWP391.horserace.horses.service.HorseService;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.tournaments.entity.Tournament;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HorseServiceImpl implements HorseService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final int MAX_PAGE_SIZE = 100;

    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final ImageUploadService imageUploadService;
    private final RegistrationRepository registrationRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RaceResultRepository raceResultRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<HorseResponse> listHorses(HorseFilterRequest filter, UUID currentUserId) {
        UUID ownerFilter = resolveOwnerFilter(filter.getOwnerUserId(), currentUserId);
        return horseRepository
                .findAll(HorseSpecification.withFilters(filter, ownerFilter), buildPageable(filter))
                .map(this::mapToResponse);
    }

    /**
     * Resolves the {@code ownerUserId} query value: the literal {@code "me"} → the caller's id
     * (requires authentication), a UUID string → that id, blank/null → no owner filter. An
     * unparseable value is a 400 rather than a 500.
     */
    private UUID resolveOwnerFilter(String raw, UUID currentUserId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.equalsIgnoreCase("me")) {
            if (currentUserId == null) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            return currentUserId;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HorseResponse getHorseById(UUID horseId) {
        return mapToResponse(loadHorse(horseId));
    }

    @Override
    @Transactional
    public HorseResponse createHorse(UUID ownerUserId, HorseRequest request) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new AppException(ErrorCode.HORSE_NAME_REQUIRED);
        }
        User owner = userRepository.findByUserIdAndDeletedFalse(ownerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String microchip = trimToNull(request.microchipNo());
        // Check against the WHOLE table — the DB UNIQUE(microchip_no) also covers soft-deleted rows.
        if (microchip != null && horseRepository.existsByMicrochipNo(microchip)) {
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
                    && horseRepository.existsByMicrochipNo(mc)) {
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

        String oldImageUrl = horse.getImageUrl();
        horse.setImageUrl(imageUploadService.storeImageAsUrl(file, "horses"));
        HorseResponse response = mapToResponse(horseRepository.save(horse));
        imageUploadService.deleteByUrl(oldImageUrl); // best-effort cleanup of the replaced file
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public HorseStatsResponse getStats(UUID horseId) {
        Horse horse = loadHorse(horseId);

        // The horse's race entries; finish positions come from race_result rows for those entries.
        List<RaceEntry> entries = raceEntryRepository.findHistoryByHorseId(horseId);

        // lifetimeEarnings = SUM of prize_earned across the horse's entries.
        BigDecimal lifetimeEarnings = entries.stream()
                .map(RaceEntry::getPrizeEarned)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long starts = 0;
        int wins = 0;
        int top3 = 0;
        if (!entries.isEmpty()) {
            List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
            List<RaceResult> results = raceResultRepository.findByEntry_EntryIdIn(entryIds).stream()
                    .filter(r -> r.getFinishPosition() != null)
                    .toList();
            starts = results.size();
            for (RaceResult r : results) {
                int pos = r.getFinishPosition();
                if (pos == 1) wins++;
                if (pos <= 3) top3++;
            }
        }

        return HorseStatsResponse.builder()
                .lifetimeEarnings(lifetimeEarnings)
                .starts(starts)
                .wins(wins)
                .top3(top3)
                .grade(horse.getGrade())
                .characteristics(horse.getCharacteristics() == null
                        ? List.of()
                        : horse.getCharacteristics().stream().sorted().toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PedigreeResponse getPedigree(UUID horseId) {
        Horse h = loadHorse(horseId);
        return PedigreeResponse.builder()
                .sire(PedigreeResponse.Sire.builder()
                        .name(h.getSireName())
                        .wins(h.getSireWins())
                        .earnings(h.getSireEarnings())
                        .build())
                .dam(PedigreeResponse.Dam.builder()
                        .name(h.getDamName())
                        .wins(h.getDamWins())
                        .note(h.getDamNote())
                        .build())
                .trainer(PedigreeResponse.Trainer.builder()
                        .name(h.getTrainerName())
                        .licenseNo(h.getTrainerLicenseNo())
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalStatusResponse getMedicalStatus(UUID horseId) {
        return mapToMedicalStatus(loadHorse(horseId));
    }

    @Override
    @Transactional
    public MedicalStatusResponse updateMedicalStatus(UUID currentUserId, UUID horseId,
                                                     UpdateMedicalStatusRequest request) {
        Horse horse = loadOwnedHorse(currentUserId, horseId);

        if (request.healthStatus() != null) {
            horse.setHealthStatus(request.healthStatus());
        }
        if (request.medicalNote() != null) {
            horse.setMedicalNote(trimToNull(request.medicalNote()));
        }
        horse.setLastHealthCheckAt(OffsetDateTime.now());

        return mapToMedicalStatus(horseRepository.save(horse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceHistoryItemResponse> getRaceHistory(UUID horseId) {
        loadHorse(horseId); // 404 if the horse doesn't exist / is soft-deleted

        List<RaceEntry> entries = raceEntryRepository.findHistoryByHorseId(horseId);
        if (entries.isEmpty()) {
            return List.of();
        }

        // Batch-load finish positions for these entries (one result row per entry, if any).
        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
        Map<UUID, Integer> finishByEntry = raceResultRepository.findByEntry_EntryIdIn(entryIds).stream()
                .filter(r -> r.getEntry() != null && r.getFinishPosition() != null)
                .collect(Collectors.toMap(r -> r.getEntry().getEntryId(), RaceResult::getFinishPosition,
                        (a, b) -> a));

        return entries.stream()
                .map(e -> mapToHistoryItem(e, finishByEntry.get(e.getEntryId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RideIntelligenceResponse getRideIntelligence(UUID horseId) {
        Horse horse = loadHorse(horseId); // 404 HORSE_NOT_FOUND if missing

        // entries are newest-scheduled first
        List<RaceEntry> entries = raceEntryRepository.findHistoryByHorseId(horseId);

        // recentForm: up-to-3 most-recent finish positions joined "-", e.g. "1-2-1"
        String recentForm = null;
        OffsetDateTime postTime = null;
        if (!entries.isEmpty()) {
            List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
            Map<UUID, Integer> finishByEntry = raceResultRepository.findByEntry_EntryIdIn(entryIds).stream()
                    .filter(r -> r.getEntry() != null && r.getFinishPosition() != null)
                    .collect(Collectors.toMap(r -> r.getEntry().getEntryId(), RaceResult::getFinishPosition,
                            (a, b) -> a));

            String form = entries.stream()
                    .map(e -> finishByEntry.get(e.getEntryId()))
                    .filter(java.util.Objects::nonNull)
                    .limit(3)
                    .map(String::valueOf)
                    .collect(Collectors.joining("-"));
            if (!form.isEmpty()) {
                recentForm = form;
            }

            // postTime: the horse's next SCHEDULED/OPEN race start (earliest upcoming).
            postTime = entries.stream()
                    .map(RaceEntry::getRace)
                    .filter(r -> r != null
                            && (r.getStatus() == RaceStatus.SCHEDULED || r.getStatus() == RaceStatus.OPEN)
                            && r.getScheduledStartAt() != null)
                    .map(Race::getScheduledStartAt)
                    .min(OffsetDateTime::compareTo)
                    .orElse(null);
        }

        return RideIntelligenceResponse.builder()
                .preferredSurface(derivePreferredSurface(horse.getCharacteristics()))
                .postTime(postTime)
                .trainer(horse.getTrainerName())
                .owner(horse.getOwner() != null ? horse.getOwner().getFullName() : null)
                .recentForm(recentForm)
                .formNotes(null)
                .build();
    }

    /**
     * Derive a preferred surface from the horse's characteristic tags.
     * A tag containing "TURF" → "TURF"; "DIRT" → "DIRT"; otherwise null.
     */
    private String derivePreferredSurface(java.util.Set<String> characteristics) {
        if (characteristics == null) {
            return null;
        }
        for (String tag : characteristics) {
            if (tag == null) continue;
            String upper = tag.toUpperCase();
            if (upper.contains("TURF")) {
                return "TURF";
            }
            if (upper.contains("DIRT")) {
                return "DIRT";
            }
        }
        return null;
    }

    @Override
    @Transactional
    public RaceEntryResponse assignHorseToRace(UUID currentUserId, UUID horseId,
                                               AssignHorseToRaceRequest request) {
        Horse horse = loadOwnedHorse(currentUserId, horseId);

        Race race = raceRepository.findByRaceIdAndDeletedFalse(request.raceId())
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        if (race.getStatus() != RaceStatus.SCHEDULED && race.getStatus() != RaceStatus.OPEN) {
            throw new AppException(ErrorCode.RACE_NOT_OPEN_FOR_ENTRY);
        }

        UUID tournamentId = race.getTournament() != null
                ? race.getTournament().getTournamentId() : null;
        if (tournamentId == null) {
            throw new AppException(ErrorCode.HORSE_NO_APPROVED_REGISTRATION);
        }

        TournamentRegistration registration = registrationRepository
                .findFirstByHorse_HorseIdAndTournament_TournamentIdAndStatus(
                        horse.getHorseId(), tournamentId, RegistrationStatus.APPROVED)
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NO_APPROVED_REGISTRATION));

        RaceEntry entry = RaceEntry.builder()
                .registration(registration)
                .race(race)
                .entryCode(generateEntryCode())
                .entryNo(request.entryNo())
                .laneNo(request.laneNo())
                .status(RaceEntryStatus.ENTERED)
                .build();

        // DB UNIQUE (race_id, registration_id)/(race_id, lane_no)/(race_id, entry_no)
        // guards dupes -> DataIntegrityViolationException -> existing 409 handler.
        return mapToEntryResponse(raceEntryRepository.save(entry));
    }

    // ── helpers ──

    private Horse loadHorse(UUID horseId) {
        return horseRepository.findByHorseIdAndDeletedFalse(horseId)
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));
    }

    /** Load a horse the caller may mutate: the owner, or any ADMIN. */
    private Horse loadOwnedHorse(UUID currentUserId, UUID horseId) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Horse horse = loadHorse(horseId);
        if (horse.getOwner() != null && horse.getOwner().getUserId().equals(currentUserId)) {
            return horse;
        }
        User current = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        if (current.getRole() != null && ADMIN_ROLE_CODE.equals(current.getRole().getRoleCode())) {
            return horse;
        }
        throw new AppException(ErrorCode.NOT_HORSE_OWNER);
    }

    /** Sequential code HRSnnnn, skipping any already taken (the DB UNIQUE is the final guard). */
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
        int size = (f.getSize() != null && f.getSize() > 0) ? Math.min(f.getSize(), MAX_PAGE_SIZE) : 10;
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

    /** Sequential code ENTnnnnn, skipping any already taken (the DB UNIQUE is the final guard). */
    private String generateEntryCode() {
        long n = raceEntryRepository.count() + 1;
        String code;
        do {
            code = String.format("ENT%05d", n++);
        } while (raceEntryRepository.existsByEntryCode(code));
        return code;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private MedicalStatusResponse mapToMedicalStatus(Horse h) {
        return MedicalStatusResponse.builder()
                .horseId(h.getHorseId())
                .horseName(h.getName())
                .healthStatus(h.getHealthStatus())
                .lastHealthCheckAt(h.getLastHealthCheckAt())
                .medicalNote(h.getMedicalNote())
                .vaccinationsUpToDate(h.isVaccinationsUpToDate())
                .recoveryPercent(h.getRecoveryPercent())
                .build();
    }

    private RaceHistoryItemResponse mapToHistoryItem(RaceEntry e, Integer finishPosition) {
        Race race = e.getRace();
        Tournament t = race != null ? race.getTournament() : null;
        return RaceHistoryItemResponse.builder()
                .raceId(race != null ? race.getRaceId() : null)
                .raceCode(race != null ? race.getRaceCode() : null)
                .raceName(race != null ? race.getName() : null)
                .tournamentId(t != null ? t.getTournamentId() : null)
                .tournamentName(t != null ? t.getName() : null)
                .scheduledStartAt(race != null ? race.getScheduledStartAt() : null)
                .entryStatus(e.getStatus() != null ? e.getStatus().name() : null)
                .finishPosition(finishPosition)
                .entryCode(e.getEntryCode())
                .venue(t != null ? t.getLocation() : null)
                .prizeEarned(e.getPrizeEarned())
                .build();
    }

    private RaceEntryResponse mapToEntryResponse(RaceEntry e) {
        TournamentRegistration reg = e.getRegistration();
        Horse horse = reg != null ? reg.getHorse() : null;
        User owner = reg != null ? reg.getOwner() : null;
        return RaceEntryResponse.builder()
                .entryId(e.getEntryId())
                .entryCode(e.getEntryCode())
                .entryNo(e.getEntryNo())
                .laneNo(e.getLaneNo())
                .status(e.getStatus())
                .raceId(e.getRace() != null ? e.getRace().getRaceId() : null)
                .registrationId(reg != null ? reg.getRegistrationId() : null)
                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getName() : null)
                .ownerUserId(owner != null ? owner.getUserId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .createdAt(e.getCreatedAt())
                .build();
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
                .fitnessCertified(h.isFitnessCertified())
                .fitnessCertExpiresAt(h.getFitnessCertExpiresAt())
                .passportScanStatus(h.getPassportScanStatus())
                .cogginsTestDate(h.getCogginsTestDate())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
