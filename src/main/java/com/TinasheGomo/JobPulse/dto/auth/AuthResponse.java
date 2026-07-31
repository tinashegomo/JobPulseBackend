package com.TinasheGomo.JobPulse.dto.auth;

import com.TinasheGomo.JobPulse.dto.alert.AlertResponse;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.entity.User;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private UUID id;
    private String email;
    private String fullName;
    private Boolean emailVerified;
    private List<AlertResponse> alerts;
    private ResumeProfileResponse resumeProfile;

    public static AuthResponse from(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .emailVerified(user.getEmailVerified())
                .build();
    }
}