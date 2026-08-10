package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.entity.UserJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJobRepository extends JpaRepository<UserJob, UUID> {
    List<UserJob> findByUserAndHiddenFalseOrderByScoreDesc(User user);
    List<UserJob> findByUserOrderByScoreDesc(User user);
    List<UserJob> findByUserAndNotifiedAtIsNullOrderByScoreDesc(User user);
    Optional<UserJob> findByUserAndJob(User user, Job job);
    void deleteAllByUser(User user);
    boolean existsByUserIdAndJobSourceAndJobExternalJobId(UUID userId, String source, String externalJobId);
}