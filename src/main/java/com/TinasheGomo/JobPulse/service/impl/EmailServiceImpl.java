package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Async
    public void sendVerificationEmail(String to, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String subject = "Verify Your Email - JobPulse";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #333;">Verify Your Email Address</h2>
                    <p>Thank you for registering with JobPulse. Please click the button below to verify your email address:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #4CAF50; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">Verify Email</a>
                    </div>
                    <p style="color: #666; font-size: 14px;">If the button doesn't work, copy and paste this link into your browser:</p>
                    <p style="color: #666; font-size: 14px; word-break: break-all;">%s</p>
                    <p style="color: #666; font-size: 14px;">This link will expire in 24 hours.</p>
                </div>
                """.formatted(link, link);

        sendHtmlEmail(to, subject, htmlContent, "verification");
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String to, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String subject = "Reset Your Password - JobPulse";
        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #333;">Reset Your Password</h2>
                    <p>You requested a password reset. Please click the button below to set a new password:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #2196F3; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">Reset Password</a>
                    </div>
                    <p style="color: #666; font-size: 14px;">If the button doesn't work, copy and paste this link into your browser:</p>
                    <p style="color: #666; font-size: 14px; word-break: break-all;">%s</p>
                    <p style="color: #666; font-size: 14px;">This link will expire in 1 hour.</p>
                    <p style="color: #999; font-size: 13px;">If you didn't request this, please ignore this email.</p>
                </div>
                """.formatted(link, link);

        sendHtmlEmail(to, subject, htmlContent, "password reset");
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent, String emailType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("{} email sent to {}", emailType, to);
        } catch (MessagingException e) {
            log.error("Failed to send {} email to {}: {}", emailType, to, e.getMessage());
        }
    }
}
