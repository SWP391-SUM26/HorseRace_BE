package com.SWP391.horserace.jockeys.service.impl;

import com.SWP391.horserace.jockeys.dto.JockeyFilterRequest;
import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.jockeys.repository.JockeyProfileSpecification;
import com.SWP391.horserace.jockeys.service.JockeyService;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JockeyServiceImpl implements JockeyService {

    private final JockeyProfileRepository jockeyProfileRepository;

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
                .build();
    }
}

