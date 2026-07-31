package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileRequest;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.security.AuthUser;
import com.TinasheGomo.JobPulse.service.ResumeProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume-profiles")
@RequiredArgsConstructor
public class ResumeProfileController {

    private final ResumeProfileService resumeProfileService;

    @PostMapping
    public ResponseEntity<ResumeProfileResponse> createOrUpdateProfile(
            @Valid @RequestBody ResumeProfileRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(resumeProfileService.createOrUpdateProfile(request, authUser.getUser()));
    }

    @GetMapping("/me")
    public ResponseEntity<ResumeProfileResponse> getProfile(
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(resumeProfileService.getProfileByUser(authUser.getUser()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteProfile(
            @AuthenticationPrincipal AuthUser authUser) {
        resumeProfileService.deleteProfile(authUser.getUser());
        return ResponseEntity.noContent().build();
    }
}