package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.dto.auth.AuthResponse;
import com.TinasheGomo.JobPulse.dto.auth.LoginRequest;
import com.TinasheGomo.JobPulse.dto.auth.RegisterRequest;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.security.AuthUser;
import com.TinasheGomo.JobPulse.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(@AuthenticationPrincipal AuthUser authUser) {
        User user = authUser.getUser();
        return ResponseEntity.ok(AuthResponse.from(user, null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@AuthenticationPrincipal AuthUser authUser) {
        User user = authUser.getUser();
        return ResponseEntity.ok(authService.refreshToken(user));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        authService.forgotPassword(email);
        return ResponseEntity.ok(Map.of("message", "Password reset email sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");
        authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }
}