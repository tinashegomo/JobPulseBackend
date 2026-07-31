package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileRequest;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.entity.User;

public interface ResumeProfileService {
    ResumeProfileResponse createOrUpdateProfile(ResumeProfileRequest request, User user);
    ResumeProfileResponse getProfileByUser(User user);
    void deleteProfile(User user);
}