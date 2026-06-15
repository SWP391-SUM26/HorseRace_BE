package com.SWP391.horserace.staffing.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.staffing.dto.CreateStaffRequest;
import com.SWP391.horserace.staffing.dto.StaffFilterRequest;
import com.SWP391.horserace.staffing.dto.StaffResponse;
import com.SWP391.horserace.staffing.dto.UpdateStaffRequest;
import com.SWP391.horserace.staffing.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for staff (referee) management.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code GET  /api/v1/staffing/staff}       — list, search, filter, paginate (tasks 136–142)</li>
 *   <li>{@code POST /api/v1/staffing/staff}        — create a new referee (task 144)</li>
 *   <li>{@code PUT  /api/v1/staffing/staff/{id}}   — update a referee (task 146)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/staffing/staff")
@RequiredArgsConstructor
public class StaffingController {

    private final StaffService staffService;

    // -------------------------------------------------------------------------
    // GET /api/v1/staffing/staff — Get Staff List / Search / Filter / Pagination
    // -------------------------------------------------------------------------
    /**
     * List referee staff members with optional search, filter, and pagination.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code search}  — search by name, email, or user code</li>
     *   <li>{@code status}  — ACTIVE, INACTIVE, SUSPENDED, BANNED</li>
     *   <li>{@code page}    — page number (0-indexed, default 0)</li>
     *   <li>{@code size}    — page size (default 10)</li>
     *   <li>{@code sortBy}  — fullName (default), email, createdAt</li>
     *   <li>{@code sortDir} — asc (default) / desc</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<Page<StaffResponse>> getStaffList(@ModelAttribute StaffFilterRequest filter) {
        Page<StaffResponse> page = staffService.getStaffList(filter);

        return ApiResponse.<Page<StaffResponse>>builder()
                .success(true)
                .message("Fetched staff list")
                .data(page)
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/staffing/staff — Create Staff
    // -------------------------------------------------------------------------
    /**
     * Create a new referee user.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StaffResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        StaffResponse response = staffService.createStaff(request);

        return ApiResponse.<StaffResponse>builder()
                .success(true)
                .message("Staff member created successfully")
                .data(response)
                .build();
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/staffing/staff/{id} — Update Staff
    // -------------------------------------------------------------------------
    /**
     * Partial update of a referee's profile (name, phone, avatar, status).
     */
    @PutMapping("/{id}")
    public ApiResponse<StaffResponse> updateStaff(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateStaffRequest request) {
        StaffResponse response = staffService.updateStaff(id, request);

        return ApiResponse.<StaffResponse>builder()
                .success(true)
                .message("Staff member updated successfully")
                .data(response)
                .build();
    }
}
