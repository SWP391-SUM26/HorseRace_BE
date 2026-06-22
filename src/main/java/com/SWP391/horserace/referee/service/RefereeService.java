package com.SWP391.horserace.referee.service;

import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.referee.dto.CreateReportRequest;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;
import com.SWP391.horserace.referee.dto.ReportFilterRequest;
import com.SWP391.horserace.referee.dto.ReportResponse;
import com.SWP391.horserace.referee.dto.UpdateReportRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface RefereeService {

    MedicalStatusResponse recordHealthCheck(UUID currentUserId, UUID horseId, HealthCheckRequest request);

    ReportResponse createReport(UUID currentUserId, CreateReportRequest request);

    Page<ReportResponse> listReports(ReportFilterRequest filter);

    ReportResponse updateReport(UUID currentUserId, UUID reportId, UpdateReportRequest request);

    ReportResponse submitReport(UUID currentUserId, UUID reportId);
}
