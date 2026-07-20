package com.SWP391.horserace.owner.repository;

import com.SWP391.horserace.owner.entity.OwnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Persistence for {@link OwnerProfile} (1:1 extension of a HORSE_OWNER user, PK = user id). */
@Repository
public interface OwnerProfileRepository extends JpaRepository<OwnerProfile, UUID> {
}
