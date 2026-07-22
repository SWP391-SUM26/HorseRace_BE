package com.SWP391.horserace.roles.service.impl;

import com.SWP391.horserace.roles.dto.PermissionResponse;
import com.SWP391.horserace.roles.dto.RoleResponse;
import com.SWP391.horserace.roles.entity.Permission;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.PermissionRepository;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.roles.service.RoleService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getRoleCode))
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
                .map(p -> PermissionResponse.builder()
                        .permissionId(p.getPermissionId())
                        .code(p.getCode())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public RoleResponse replacePermissions(UUID roleId, List<String> permissionCodes) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        List<String> codes = permissionCodes != null ? permissionCodes : List.of();
        List<Permission> resolved = codes.isEmpty()
                ? List.of()
                : permissionRepository.findByCodeIn(codes);

        // Reject unknown codes outright rather than silently granting a smaller set than the caller
        // asked for — a half-applied permission change is worse than a rejected one.
        if (resolved.size() != new HashSet<>(codes).size()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_EXISTED);
        }

        // The @ManyToMany is LAZY with no cascade, so the Permission rows have to be loaded first
        // (above) and then attached; mutating the managed collection is what writes role_permission.
        role.getPermissions().clear();
        role.getPermissions().addAll(resolved);
        return mapToResponse(roleRepository.save(role));
    }

    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .status(role.getStatus() != null ? role.getStatus().name() : null)
                .userCount(userRepository.countByRole_RoleCodeAndDeletedFalse(role.getRoleCode()))
                .permissionCodes(permissionRepository.findPermissionCodesByRoleId(role.getRoleId())
                        .stream().sorted().toList())
                .build();
    }
}
