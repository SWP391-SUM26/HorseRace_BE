package com.SWP391.horserace.staffing.service;

import com.SWP391.horserace.staffing.dto.CreateStaffRequest;
import com.SWP391.horserace.staffing.dto.StaffFilterRequest;
import com.SWP391.horserace.staffing.dto.StaffResponse;
import com.SWP391.horserace.staffing.dto.UpdateStaffRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface StaffService {

    /** List referee staff with search, filter, and pagination (tasks 136, 138, 140, 142). */
    Page<StaffResponse> getStaffList(StaffFilterRequest filter);

    /** Create a new referee user (task 144). */
    StaffResponse createStaff(CreateStaffRequest request);

    /** Partial update of a referee user (task 146). */
    StaffResponse updateStaff(UUID userId, UpdateStaffRequest request);
}
