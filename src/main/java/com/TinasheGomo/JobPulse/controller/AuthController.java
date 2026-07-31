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
}