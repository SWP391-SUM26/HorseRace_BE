package com.SWP391.horserace.roles.repository;

import com.SWP391.horserace.roles.entity.Permission;
import com.SWP391.horserace.roles.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    /**
     * Resolve the permission CODES granted to a role via the {@code role_permission} join table.
     *
     * <p>Walks the {@link Role#getPermissions()} {@code @ManyToMany} mapping (which maps the
     * {@code role_permission} table) and projects each linked {@link Permission}'s {@code code}.
     * Returns an empty list when the role has no permissions.
     */
    @Query("SELECT p.code FROM Role r JOIN r.permissions p WHERE r.roleId = :roleId")
    List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);
}
