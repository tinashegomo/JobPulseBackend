package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.auth.AuthResponse;
import com.TinasheGomo.JobPulse.dto.auth.LoginRequest;
import com.TinasheGomo.JobPulse.dto.auth.RegisterRequest;
import com.TinasheGomo.JobPulse.entity.EmailVerification;
import com.TinasheGomo.JobPulse.entity.PasswordResetToken;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.exception.exceptions.DuplicateException;
import com.TinasheGomo.JobPulse.repository.EmailVerificationRepository;
import com.TinasheGomo.JobPulse.repository.PasswordResetTokenRepository;
import com.TinasheGomo.JobPulse.repository.UserRepository;
import com.TinasheGomo.JobPulse.security.JWTUtils;
import com.TinasheGomo.JobPulse.service.AuthService;
import com.TinasheGomo.JobPulse.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        String verificationToken = UUID.randomUUID().toString();
        EmailVerification emailVerification = EmailVerification.builder()
                .user(user)
                .token(verificationToken)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        emailVerificationRepository.save(emailVerification);

        emailService.sendVerificationEmail(user.getEmail(), verificationToken);

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

    @Override
    public void verifyEmail(String token) {
        EmailVerification verification = emailVerificationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verification.getUsed()) {
            throw new IllegalArgumentException("Verification token already used");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        User user = verification.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verification.setUsed(true);
        emailVerificationRepository.save(verification);
    }

    @Override
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email"));

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String resetToken = UUID.randomUUID().toString();
        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .user(user)
                .token(resetToken)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        passwordResetTokenRepository.save(passwordResetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (resetToken.getUsed()) {
            throw new IllegalArgumentException("Reset token already used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }
}