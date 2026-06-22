package com.SWP391.horserace.reports.repository;

import com.SWP391.horserace.reports.entity.RefereeReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefereeReportRepository
        extends JpaRepository<RefereeReport, UUID>, JpaSpecificationExecutor<RefereeReport> {
}
