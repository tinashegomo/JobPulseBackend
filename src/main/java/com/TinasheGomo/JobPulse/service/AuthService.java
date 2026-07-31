package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.dto.auth.AuthResponse;
import com.TinasheGomo.JobPulse.dto.auth.LoginRequest;
import com.TinasheGomo.JobPulse.dto.auth.RegisterRequest;
import com.TinasheGomo.JobPulse.entity.User;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(User user);
    User getCurrentUser(String email);
    void verifyEmail(String token);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
}