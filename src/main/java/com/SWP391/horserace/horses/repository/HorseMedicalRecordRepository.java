package com.SWP391.horserace.horses.repository;

import com.SWP391.horserace.horses.entity.HorseMedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HorseMedicalRecordRepository extends JpaRepository<HorseMedicalRecord, UUID> {

    List<HorseMedicalRecord> findByHorse_HorseIdOrderByRecordDateDescCreatedAtDesc(UUID horseId);

    Optional<HorseMedicalRecord> findByRecordIdAndHorse_HorseId(UUID recordId, UUID horseId);
}
