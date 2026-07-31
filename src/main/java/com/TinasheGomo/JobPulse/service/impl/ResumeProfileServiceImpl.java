package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileRequest;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.entity.ResumeProfile;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.mapper.ResumeProfileMapper;
import com.TinasheGomo.JobPulse.repository.ResumeProfileRepository;
import com.TinasheGomo.JobPulse.service.ResumeProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResumeProfileServiceImpl implements ResumeProfileService {

    private final ResumeProfileRepository resumeProfileRepository;
    private final ResumeProfileMapper resumeProfileMapper;

    @Override
    public ResumeProfileResponse createOrUpdateProfile(ResumeProfileRequest request, User user) {
        ResumeProfile profile = resumeProfileRepository.findByUser(user)
                .orElseGet(() -> {
                    ResumeProfile newProfile = new ResumeProfile();
                    newProfile.setUser(user);
                    return newProfile;
                });

        resumeProfileMapper.updateEntityFromRequest(request, profile);
        resumeProfileRepository.save(profile);
        return resumeProfileMapper.toResponse(profile);
    }

    @Override
    public ResumeProfileResponse getProfileByUser(User user) {
        return resumeProfileRepository.findByUser(user)
                .map(resumeProfileMapper::toResponse)
                .orElse(null);
    }

    @Override
    public void deleteProfile(User user) {
        resumeProfileRepository.findByUser(user)
                .ifPresent(resumeProfileRepository::delete);
    }
}