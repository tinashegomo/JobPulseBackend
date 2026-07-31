package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.auth.AuthResponse;
import com.TinasheGomo.JobPulse.dto.auth.LoginRequest;
import com.TinasheGomo.JobPulse.dto.auth.RegisterRequest;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.exception.exceptions.DuplicateException;
import com.TinasheGomo.JobPulse.repository.UserRepository;
import com.TinasheGomo.JobPulse.security.JWTUtils;
import com.TinasheGomo.JobPulse.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .emailVerified(true)
                .build();

        userRepository.save(user);

        String token = jwtUtils.generateToken(user.getEmail());
        return AuthResponse.from(user, token);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        String token = jwtUtils.generateToken(user.getEmail());
        return AuthResponse.from(user, token);
    }

    @Override
    public AuthResponse refreshToken(User user) {
        String token = jwtUtils.generateToken(user.getEmail());
        return AuthResponse.from(user, token);
    }

    @Override
    public User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
