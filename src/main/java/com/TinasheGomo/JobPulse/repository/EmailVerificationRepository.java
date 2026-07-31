package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.EmailVerification;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {
    Optional<EmailVerification> findByToken(String token);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerification ev WHERE ev.user.id = :userId")
    void deleteByUserId(UUID userId);
}
