package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.ResumeProfile;
import com.TinasheGomo.JobPulse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResumeProfileRepository extends JpaRepository<ResumeProfile, UUID> {
    Optional<ResumeProfile> findByUser(User user);
}