package com.SWP391.horserace.users.service;

import com.SWP391.horserace.users.dto.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse getUserById(UUID id);
    List<UserResponse> getAllUsers();
}
