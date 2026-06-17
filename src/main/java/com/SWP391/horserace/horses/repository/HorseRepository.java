package com.SWP391.horserace.horses.repository;

import com.SWP391.horserace.horses.entity.Horse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface HorseRepository
        extends JpaRepository<Horse, UUID>, JpaSpecificationExecutor<Horse> {

    Optional<Horse> findByHorseIdAndDeletedFalse(UUID horseId);

    boolean existsByHorseCode(String horseCode);

    /** Table-wide check — matches the DB UNIQUE(microchip_no), which also covers soft-deleted rows. */
    boolean existsByMicrochipNo(String microchipNo);
}
