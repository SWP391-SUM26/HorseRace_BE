package com.SWP391.horserace.users.repository;

import com.SWP391.horserace.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Active (non soft-deleted) users only. */
    List<User> findAllByDeletedFalse();

    /** A single non soft-deleted user by id. */
    Optional<User> findByUserIdAndDeletedFalse(UUID userId);

    /** Handy for auth/registration later. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
