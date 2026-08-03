package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.JobProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobProfileRepository extends JpaRepository<JobProfile, UUID> {

    Optional<JobProfile> findBySourceAndExternalJobId(String source, String externalJobId);

    boolean existsBySourceAndExternalJobId(String source, String externalJobId);

    @Query("SELECT jp FROM JobProfile jp WHERE jp.source = :source AND jp.externalJobId IN :externalIds")
    List<JobProfile> findBySourceAndExternalIds(String source, List<String> externalIds);

    @Query("SELECT jp.externalJobId FROM JobProfile jp WHERE jp.source = :source AND jp.externalJobId IN :externalIds")
    List<String> findExistingExternalIds(String source, List<String> externalIds);
}
