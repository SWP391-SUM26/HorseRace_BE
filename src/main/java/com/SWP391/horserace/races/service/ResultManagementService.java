package com.SWP391.horserace.races.service;

import com.SWP391.horserace.races.dto.GlobalResultResponse;
import com.SWP391.horserace.races.dto.ResultFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ResultManagementService {
    Page<GlobalResultResponse> getGlobalResults(ResultFilterRequest filter, Pageable pageable);
}
