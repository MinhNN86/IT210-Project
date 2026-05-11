package com.projectit210.service;

import com.projectit210.dto.request.ProfileUpdateRequest;
import com.projectit210.entity.User;

/**
 * Service quản lý hồ sơ cá nhân (CORE-03)
 */
public interface ProfileService {

    User getProfile(String userId);

    User updateProfile(String userId, ProfileUpdateRequest request);
}
