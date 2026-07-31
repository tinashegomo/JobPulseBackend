package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.ApiKey;
import com.TinasheGomo.JobPulse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByUserAndActiveTrue(User user);
    Optional<ApiKey> findByUserAndProviderAndActiveTrue(User user, String provider);
}