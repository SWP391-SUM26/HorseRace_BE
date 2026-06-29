package com.SWP391.horserace.users.repository;

import com.SWP391.horserace.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Active (non soft-deleted) users only. */
    List<User> findAllByDeletedFalse();

    /** A single non soft-deleted user by id. */
    Optional<User> findByUserIdAndDeletedFalse(UUID userId);

    /** Handy for auth/registration later. */
    Optional<User> findByEmail(String email);

    /** Login lookup — active accounts only. */
    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByEmail(String email);

    /** All active users with a given role code (e.g. "ADMIN") — used to notify admins. */
    List<User> findByRole_RoleCodeAndDeletedFalse(String roleCode);

    /** Total non soft-deleted users (powers the Total Users KPI). */
    long countByDeletedFalse();

    /**
     * Non-deleted user counts grouped by role code. Each row is {@code [roleCode, count]}.
     * Users without a role are excluded (their {@code role.roleCode} is null).
     */
    @Query("SELECT u.role.roleCode, COUNT(u) FROM User u "
            + "WHERE u.deleted = false AND u.role IS NOT NULL "
            + "GROUP BY u.role.roleCode")
    List<Object[]> countByRoleCodeGrouped();

    /**
     * Non-deleted user counts grouped by status. Each row is {@code [UserStatus, count]}.
     */
    @Query("SELECT u.status, COUNT(u) FROM User u "
            + "WHERE u.deleted = false "
            + "GROUP BY u.status")
    List<Object[]> countByStatusGrouped();
}
