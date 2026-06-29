package com.SWP391.horserace.tournaments.service.impl;

import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.dto.EligibilityDto;
import com.SWP391.horserace.tournaments.dto.TournamentFilterRequest;
import com.SWP391.horserace.tournaments.dto.TournamentRequest;
import com.SWP391.horserace.tournaments.dto.TournamentResponse;
import com.SWP391.horserace.tournaments.entity.EligibilityCriteria;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import com.SWP391.horserace.tournaments.entity.TournamentVenue;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.tournaments.service.TournamentService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.venues.dto.VenueResponse;
import com.SWP391.horserace.venues.entity.Venue;
import com.SWP391.horserace.venues.repository.VenueRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final RegistrationRepository registrationRepository;

    @Override
    @Transactional
    public TournamentResponse createTournament(TournamentRequest request, UUID userId) {
        if (tournamentRepository.existsByTournamentCode(request.getTournamentCode())) {
            throw new AppException(ErrorCode.TOURNAMENT_CODE_EXISTED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Tournament tournament = Tournament.builder()
                .tournamentCode(request.getTournamentCode())
                .name(request.getName())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .registrationOpenAt(request.getRegistrationOpenAt())
                .registrationCloseAt(request.getRegistrationCloseAt())
                .location(request.getLocation())
                .circuitTier(request.getCircuitTier())
                .totalPurse(request.getTotalPurse())
                .entryCap(request.getEntryCap())
                .eligibility(toEligibilityEntity(request.getEligibility()))
                .status(request.getStatus() != null ? request.getStatus() : TournamentStatus.DRAFT)
                .createdBy(user)
                .build();

        applyVenues(tournament, request.getVenueIds());

        tournament = tournamentRepository.save(tournament);
        return mapToResponse(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TournamentResponse> getTournaments(TournamentFilterRequest filter) {
        Sort sort = buildSort(filter.getSortBy(), filter.getSortDir());
        Pageable pageable = PageRequest.of(
                filter.getPage() != null ? filter.getPage() : 0,
                filter.getSize() != null ? filter.getSize() : 10,
                sort);

        Specification<Tournament> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Only non-deleted tournaments
            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));

            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), 
                        "%" + filter.getName().toLowerCase() + "%"));
            }
            if (filter.getTournamentCode() != null && !filter.getTournamentCode().isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("tournamentCode")), 
                        "%" + filter.getTournamentCode().toLowerCase() + "%"));
            }
            if (filter.getLocation() != null && !filter.getLocation().isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("location")), 
                        "%" + filter.getLocation().toLowerCase() + "%"));
            }
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Tournament> page = tournamentRepository.findAll(spec, pageable);
        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TournamentResponse getTournamentById(UUID id) {
        Tournament tournament = tournamentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }
        return mapToResponse(tournament);
    }

    @Override
    @Transactional
    public TournamentResponse updateTournament(UUID id, TournamentRequest request) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        if (!tournament.getTournamentCode().equals(request.getTournamentCode()) && 
            tournamentRepository.existsByTournamentCode(request.getTournamentCode())) {
            throw new AppException(ErrorCode.TOURNAMENT_CODE_EXISTED);
        }

        tournament.setTournamentCode(request.getTournamentCode());
        tournament.setName(request.getName());
        tournament.setDescription(request.getDescription());
        tournament.setStartDate(request.getStartDate());
        tournament.setEndDate(request.getEndDate());
        tournament.setRegistrationOpenAt(request.getRegistrationOpenAt());
        tournament.setRegistrationCloseAt(request.getRegistrationCloseAt());
        tournament.setLocation(request.getLocation());
        tournament.setCircuitTier(request.getCircuitTier());
        tournament.setTotalPurse(request.getTotalPurse());
        tournament.setEntryCap(request.getEntryCap());
        tournament.setEligibility(toEligibilityEntity(request.getEligibility()));

        // Re-sync venue links only when the client supplies a venueIds list (null = leave untouched).
        if (request.getVenueIds() != null) {
            tournament.getVenues().clear();
            applyVenues(tournament, request.getVenueIds());
        }

        tournament = tournamentRepository.save(tournament);
        return mapToResponse(tournament);
    }

    @Override
    @Transactional
    public void deleteTournament(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        tournament.setDeleted(true);
        tournament.setDeletedAt(OffsetDateTime.now());
        tournamentRepository.save(tournament);
    }

    @Override
    @Transactional
    public TournamentResponse publishTournament(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
                
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS, "Only DRAFT tournaments can be published");
        }

        tournament.setStatus(TournamentStatus.PUBLISHED);
        tournament = tournamentRepository.save(tournament);
        
        return mapToResponse(tournament);
    }

    @Override
    @Transactional
    public TournamentResponse closeRegistration(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
                
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        if (tournament.getStatus() != TournamentStatus.REGISTRATION_OPEN
                && tournament.getStatus() != TournamentStatus.PUBLISHED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS, "Tournament is not open for registration");
        }

        tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED);
        tournament = tournamentRepository.save(tournament);

        return mapToResponse(tournament);
    }

    // =========================================================================
    // §C5 — STATUS TRANSITIONS
    // Canonical set: DRAFT → PUBLISHED → REGISTRATION_OPEN → REGISTRATION_CLOSED
    //                → ONGOING → COMPLETED  (+ CANCELLED). Matches the schema CHECK.
    // =========================================================================

    @Override
    @Transactional
    public TournamentResponse openRegistration(UUID id) {
        Tournament tournament = loadActive(id);
        if (tournament.getStatus() != TournamentStatus.PUBLISHED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS,
                    "Only PUBLISHED tournaments can open registration");
        }
        tournament.setStatus(TournamentStatus.REGISTRATION_OPEN);
        return mapToResponse(tournamentRepository.save(tournament));
    }

    @Override
    @Transactional
    public TournamentResponse startTournament(UUID id) {
        Tournament tournament = loadActive(id);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION_CLOSED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS,
                    "Only REGISTRATION_CLOSED tournaments can start");
        }
        tournament.setStatus(TournamentStatus.ONGOING);
        return mapToResponse(tournamentRepository.save(tournament));
    }

    @Override
    @Transactional
    public TournamentResponse completeTournament(UUID id) {
        Tournament tournament = loadActive(id);
        if (tournament.getStatus() != TournamentStatus.ONGOING) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS,
                    "Only ONGOING tournaments can be completed");
        }
        tournament.setStatus(TournamentStatus.COMPLETED);
        return mapToResponse(tournamentRepository.save(tournament));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Tournament loadActive(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }
        return tournament;
    }

    /** Link the given venue ids to the tournament (validates each exists). Null/empty = no change. */
    private void applyVenues(Tournament tournament, List<UUID> venueIds) {
        if (venueIds == null || venueIds.isEmpty()) {
            return;
        }
        for (UUID venueId : venueIds.stream().distinct().toList()) {
            Venue venue = venueRepository.findById(venueId)
                    .orElseThrow(() -> new AppException(ErrorCode.VENUE_NOT_FOUND));
            tournament.getVenues().add(TournamentVenue.builder()
                    .tournament(tournament)
                    .venue(venue)
                    .build());
        }
    }

    private EligibilityCriteria toEligibilityEntity(EligibilityDto dto) {
        if (dto == null) {
            return null;
        }
        return EligibilityCriteria.builder()
                .thoroughbredsOnly(dto.getThoroughbredsOnly())
                .minAgeYears(dto.getMinAgeYears())
                .requiresPreviousGroupWin(dto.getRequiresPreviousGroupWin())
                .build();
    }

    private EligibilityDto toEligibilityDto(EligibilityCriteria e) {
        if (e == null
                || (e.getThoroughbredsOnly() == null && e.getMinAgeYears() == null
                    && e.getRequiresPreviousGroupWin() == null)) {
            return null;
        }
        return EligibilityDto.builder()
                .thoroughbredsOnly(e.getThoroughbredsOnly())
                .minAgeYears(e.getMinAgeYears())
                .requiresPreviousGroupWin(e.getRequiresPreviousGroupWin())
                .build();
    }

    private List<VenueResponse> mapVenues(Tournament tournament) {
        if (tournament.getVenues() == null || tournament.getVenues().isEmpty()) {
            return List.of();
        }
        return tournament.getVenues().stream()
                .map(TournamentVenue::getVenue)
                .filter(v -> v != null)
                .map(v -> VenueResponse.builder()
                        .venueId(v.getVenueId())
                        .name(v.getName())
                        .trackName(v.getTrackName())
                        .city(v.getCity())
                        .country(v.getCountry())
                        .capacity(v.getCapacity())
                        .surface(v.getSurface())
                        .build())
                .toList();
    }

    private TournamentResponse mapToResponse(Tournament tournament) {
        long registeredCount = tournament.getTournamentId() == null ? 0L
                : registrationRepository.countByTournament_TournamentIdAndStatus(
                        tournament.getTournamentId(), RegistrationStatus.APPROVED);

        return TournamentResponse.builder()
                .tournamentId(tournament.getTournamentId())
                .tournamentCode(tournament.getTournamentCode())
                .name(tournament.getName())
                .description(tournament.getDescription())
                .startDate(tournament.getStartDate())
                .endDate(tournament.getEndDate())
                .registrationOpenAt(tournament.getRegistrationOpenAt())
                .registrationCloseAt(tournament.getRegistrationCloseAt())
                .location(tournament.getLocation())
                .circuitTier(tournament.getCircuitTier())
                .totalPurse(tournament.getTotalPurse())
                .entryCap(tournament.getEntryCap())
                .eligibility(toEligibilityDto(tournament.getEligibility()))
                .venues(mapVenues(tournament))
                .registeredEntriesCount(registeredCount)
                .status(tournament.getStatus())
                .createdAt(tournament.getCreatedAt())
                .updatedAt(tournament.getUpdatedAt())
                .build();
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = switch (sortBy != null ? sortBy : "createdAt") {
            case "name" -> "name";
            case "startDate" -> "startDate";
            case "endDate" -> "endDate";
            case "registrationOpenAt" -> "registrationOpenAt";
            default -> "createdAt";
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, field);
    }
}
