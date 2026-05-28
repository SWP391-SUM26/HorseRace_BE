package com.SWP391.horserace.users.service;

import com.SWP391.horserace.users.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse getUserById(Long id);
    List<UserResponse> getAllUsers();
}
