package com.SWP391.horserace.jockeys.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.jockeys.dto.InvitationInsightsResponse;
import com.SWP391.horserace.jockeys.dto.JockeyFilterRequest;
import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.dto.JockeyStatsResponse;
import com.SWP391.horserace.jockeys.dto.JockeySuggestionResponse;
import com.SWP391.horserace.jockeys.dto.UnassignedEntryResponse;
import com.SWP391.horserace.jockeys.dto.UpdateJockeyProfileRequest;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.jockeys.repository.JockeyProfileSpecification;
import com.SWP391.horserace.jockeys.service.JockeyService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JockeyServiceImpl implements JockeyService {

    private final JockeyProfileRepository jockeyProfileRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;
    private final RaceRepository raceRepository;
    private final HorseRepository horseRepository;
    private final RaceResultRepository raceResultRepository;

    @Override
    @Transactional(readOnly = true)
    public List<JockeyResponse> getAllJockeys() {
        return jockeyProfileRepository.findAllActiveJockeys().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public JockeyResponse getJockeyById(UUID jockeyUserId) {
        JockeyProfile profile = jockeyProfileRepository.findByIdAndUserActive(jockeyUserId)
                .orElseThrow(() -> new AppException(ErrorCode.JOCKEY_NOT_FOUND));
        return mapToResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyResponse> searchJockeys(String keyword) {
        List<JockeyProfile> profiles;
        if (keyword == null || keyword.isBlank()) {
            profiles = jockeyProfileRepository.findAllActiveJockeys();
        } else {
            profiles = jockeyProfileRepository.searchByKeyword(keyword.trim());
        }
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyResponse> filterJockeys(JockeyFilterRequest filter) {
        return jockeyProfileRepository
                .findAll(JockeyProfileSpecification.withFilters(filter))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JockeyResponse> getJockeysPaginated(int page, int size, String sortBy, String sortDir) {
        String field = switch (sortBy != null ? sortBy : "winCount") {
            case "experienceYrs" -> "experienceYrs";
            case "bodyWeight" -> "bodyWeight";
            case "heightCm" -> "heightCm";
            case "fullName" -> "jockeyUser.fullName";
            default -> "winCount";
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, field));

        return jockeyProfileRepository.findAllActiveJockeysPaged(pageable)
                .map(this::mapToResponse);
    }

    private JockeyResponse mapToResponse(JockeyProfile profile) {
        User user = profile.getJockeyUser();
        return JockeyResponse.builder()
                .userId(user.getUserId())
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .licenseNo(profile.getLicenseNo())
                .bodyWeight(profile.getBodyWeight())
                .heightCm(profile.getHeightCm())
                .experienceYrs(profile.getExperienceYrs())
                .winCount(profile.getWinCount())
                .bio(profile.getBio())
                .createdAt(profile.getCreatedAt())
                .rating(profile.getRating())
                .ridingStyle(profile.getRidingStyle())
                .winRate(profile.getWinRate())
                .recentForm(splitRecentForm(profile.getRecentForm()))
                .baseFee(profile.getBaseFee())
                .prizePercent(profile.getPrizePercent())
                .lastTrophy(profile.getLastTrophy())
                .build();
    }

    /**
     * Split the comma-joined recent_form CSV into a list; empty list when
     * null/blank.
     */
    private List<String> splitRecentForm(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnassignedEntryResponse> getUnassignedEntries(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return jockeyAssignmentRepository.findUnassignedEntriesByOwner(ownerUserId).stream()
                .map(this::mapToUnassignedEntry)
                .collect(Collectors.toList());
    }

    private UnassignedEntryResponse mapToUnassignedEntry(RaceEntry entry) {
        TournamentRegistration reg = entry.getRegistration();
        Race race = entry.getRace();
        return UnassignedEntryResponse.builder()
                .registrationId(reg.getRegistrationId())
                .horseId(reg.getHorse().getHorseId())
                .horseName(reg.getHorse().getName())
                .raceId(race.getRaceId())
                .raceName(race.getName())
                .raceDate(race.getScheduledStartAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeySuggestionResponse> getJockeySuggestions(UUID raceId, UUID horseId) {
        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));
        horseRepository.findByHorseIdAndDeletedFalse(horseId)
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));

        // Eligibility sets (FE-v2 Phase 1): a jockey is ineligible if already invited
        // for this
        // (race, horse), already riding this race, or has an accepted ride clashing on
        // start time.
        Set<UUID> alreadyInvited = Set.copyOf(
                jockeyAssignmentRepository.findJockeyIdsInvitedForRaceHorse(raceId, horseId));
        Set<UUID> ridingThisRace = Set.copyOf(
                jockeyAssignmentRepository.findJockeyIdsAcceptedInRace(raceId));
        Set<UUID> scheduleClash = race.getScheduledStartAt() == null
                ? Set.of()
                : Set.copyOf(
                        jockeyAssignmentRepository.findJockeyIdsAcceptedAtTime(raceId, race.getScheduledStartAt()));

        return jockeyProfileRepository.findAllActiveJockeys().stream()
                .map(jp -> {
                    UUID jid = jp.getJockeyUserId();
                    String reason = alreadyInvited.contains(jid) ? "ALREADY_INVITED"
                            : ridingThisRace.contains(jid) ? "RIDING_THIS_RACE"
                                    : scheduleClash.contains(jid) ? "SCHEDULE_CLASH"
                                            : null;
                    return JockeySuggestionResponse.builder()
                            .jockeyUserId(jid)
                            .compatibility(computeCompatibility(jp, horseId))
                            .eligible(reason == null)
                            .ineligibleReason(reason)
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getCompatibility(), a.getCompatibility()))
                .collect(Collectors.toList());
    }

    /**
     * Deterministic compatibility score in the inclusive range 50–99 (no ML model
     * exists).
     *
     * <p>
     * It blends a stat-based component (the jockey's win rate, or rating scaled to
     * a
     * percentage when win rate is absent) with a stable pseudo-random jitter
     * derived from
     * {@code hash(jockeyUserId, horseId)}. The same (jockey, horse) pair always
     * yields the
     * same score, and better stats trend toward higher scores.
     */
    private int computeCompatibility(JockeyProfile profile, UUID horseId) {
        // Stat component, normalised to 0–100.
        double statScore;
        if (profile.getWinRate() != null) {
            statScore = profile.getWinRate().doubleValue();
        } else if (profile.getRating() != null) {
            statScore = profile.getRating().doubleValue() / 5.0 * 100.0;
        } else {
            statScore = 50.0;
        }

        // Stable jitter (0..14) from the (jockey, horse) pair hash so the score is
        // repeatable.
        int hash = (profile.getJockeyUserId().hashCode() * 31) ^ horseId.hashCode();
        int jitter = Math.floorMod(hash, 15);

        // Weight stats 70%, base 50 for the floor, plus jitter; then clamp to 50..99.
        int raw = (int) Math.round(50 + statScore * 0.35) + jitter - 7;
        return Math.max(50, Math.min(99, raw));
    }

    // =========================================================================
    // JOCKEY-SELF endpoints (FE-v2 jockey contract #8, #1, #11)
    // =========================================================================

    @Override
    @Transactional
    public JockeyResponse updateMyProfile(UUID callerUserId, UpdateJockeyProfileRequest request) {
        if (callerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        JockeyProfile profile = jockeyProfileRepository.findByIdAndUserActive(callerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.JOCKEY_NOT_FOUND));

        // Partial update — only apply non-null fields.
        if (request.getBodyWeight() != null)
            profile.setBodyWeight(request.getBodyWeight());
        if (request.getHeightCm() != null)
            profile.setHeightCm(request.getHeightCm());
        if (request.getRidingStyle() != null)
            profile.setRidingStyle(request.getRidingStyle());
        if (request.getBio() != null)
            profile.setBio(request.getBio());
        if (request.getLicenseNo() != null)
            profile.setLicenseNo(request.getLicenseNo());
        if (request.getBaseFee() != null)
            profile.setBaseFee(request.getBaseFee());
        if (request.getPrizePercent() != null)
            profile.setPrizePercent(request.getPrizePercent());

        profile = jockeyProfileRepository.save(profile);
        return mapToResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public JockeyStatsResponse getMyStats(UUID callerUserId) {
        if (callerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        JockeyProfile profile = jockeyProfileRepository.findByIdAndUserActive(callerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.JOCKEY_NOT_FOUND));

        List<JockeyAssignment> rides = jockeyAssignmentRepository.findAcceptedRidesByJockey(callerUserId);

        // Map entry id -> result so we only count rides that have a recorded result.
        List<UUID> entryIds = rides.stream()
                .map(ja -> ja.getEntry().getEntryId())
                .collect(Collectors.toList());
        Map<UUID, RaceResult> resultByEntry = entryIds.isEmpty()
                ? Map.of()
                : raceResultRepository.findByEntry_EntryIdIn(entryIds).stream()
                        .collect(Collectors.toMap(rr -> rr.getEntry().getEntryId(), Function.identity(), (a, b) -> a));

        int totalRides = 0;
        int wins = 0;
        int places = 0;
        int top3 = 0;
        long positionSum = 0;
        BigDecimal earnings = BigDecimal.ZERO;

        for (JockeyAssignment ja : rides) {
            RaceResult result = resultByEntry.get(ja.getEntry().getEntryId());
            if (result == null || result.getFinishPosition() == null) {
                continue; // only rides with a result count
            }
            int pos = result.getFinishPosition();
            totalRides++;
            positionSum += pos;
            if (pos == 1)
                wins++;
            if (pos == 2)
                places++;
            if (pos <= 3)
                top3++;

            BigDecimal prize = ja.getEntry().getPrizeEarned();
            if (prize != null) {
                earnings = earnings.add(prize);
            }
        }

        double winRate = totalRides == 0 ? 0.0 : round1((double) wins / totalRides * 100);
        double top3Rate = totalRides == 0 ? 0.0 : round1((double) top3 / totalRides * 100);
        double avgPlacement = totalRides == 0 ? 0.0 : round1((double) positionSum / totalRides);

        return JockeyStatsResponse.builder()
                .winRate(winRate)
                .totalRides(totalRides)
                .wins(wins)
                .places(places)
                .top3Rate(top3Rate)
                .avgPlacement(avgPlacement)
                .careerWins(profile.getWinCount() != null ? profile.getWinCount() : 0)
                .seasonEarnings(earnings) // TODO season filter — no season dimension yet, equals careerEarnings
                .careerEarnings(earnings)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationInsightsResponse getMyInvitationInsights(UUID callerUserId) {
        if (callerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        jockeyProfileRepository.findByIdAndUserActive(callerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.JOCKEY_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime weekAgo = now.minusDays(7);
        OffsetDateTime twoWeeksAgo = now.minusDays(14);

        long thisWeek = jockeyAssignmentRepository.countInvitedBetween(callerUserId, weekAgo, now);
        long priorWeek = jockeyAssignmentRepository.countInvitedBetween(callerUserId, twoWeeksAgo, weekAgo);
        int weekDelta = (int) (thisWeek - priorWeek);

        List<JockeyAssignment> all = jockeyAssignmentRepository.findAllByJockeyWithInviter(callerUserId);
        int total = all.size();
        long accepted = all.stream()
                .filter(ja -> ja.getStatus() == com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus.ACCEPTED)
                .count();
        int acceptanceRate = total == 0 ? 0 : (int) Math.round((double) accepted / total * 100);

        // Group by inviter (assignedBy), top 3 by count.
        Map<UUID, InvitationInsightsResponse.OwnerActivity> byOwner = new LinkedHashMap<>();
        for (JockeyAssignment ja : all) {
            User inviter = ja.getAssignedBy();
            if (inviter == null) {
                continue;
            }
            UUID ownerId = inviter.getUserId();
            InvitationInsightsResponse.OwnerActivity oa = byOwner.get(ownerId);
            if (oa == null) {
                byOwner.put(ownerId, InvitationInsightsResponse.OwnerActivity.builder()
                        .ownerUserId(ownerId)
                        .name(inviter.getFullName())
                        .requests(1)
                        .build());
            } else {
                oa.setRequests(oa.getRequests() + 1);
            }
        }

        List<InvitationInsightsResponse.OwnerActivity> mostActive = new ArrayList<>(byOwner.values());
        mostActive.sort(Comparator.comparingInt(InvitationInsightsResponse.OwnerActivity::getRequests).reversed());
        if (mostActive.size() > 3) {
            mostActive = new ArrayList<>(mostActive.subList(0, 3));
        }

        return InvitationInsightsResponse.builder()
                .invitationsThisWeek((int) thisWeek)
                .weekDelta(weekDelta)
                .acceptanceRate(acceptanceRate)
                .mostActiveOwners(mostActive)
                .build();
    }

    /** Round a double to 1 decimal place (half-up). */
    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
