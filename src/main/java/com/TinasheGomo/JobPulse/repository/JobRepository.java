package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findBySourceAndExternalJobId(String source, String externalJobId);
    List<Job> findBySource(String source);

    @Query("SELECT j FROM Job j WHERE j.profileExtracted = false")
    List<Job> findUnextractedJobs();

    @Query("SELECT j.externalJobId FROM Job j WHERE j.source = :source AND j.externalJobId IN :externalIds")
    List<String> findExistingExternalIds(String source, List<String> externalIds);

    @Modifying
    @Query("DELETE FROM Job j WHERE j.id NOT IN (SELECT DISTINCT uj.job.id FROM UserJob uj)")
    void deleteOrphanedJobs();
}