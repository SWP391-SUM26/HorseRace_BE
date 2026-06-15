package com.SWP391.horserace.tournaments.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.dto.TournamentFilterRequest;
import com.SWP391.horserace.tournaments.dto.TournamentRequest;
import com.SWP391.horserace.tournaments.dto.TournamentResponse;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.tournaments.service.TournamentService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
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
                .status(request.getStatus() != null ? request.getStatus() : TournamentStatus.DRAFT)
                .createdBy(user)
                .build();

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
    // PRIVATE HELPERS
    // =========================================================================

    private TournamentResponse mapToResponse(Tournament tournament) {
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
