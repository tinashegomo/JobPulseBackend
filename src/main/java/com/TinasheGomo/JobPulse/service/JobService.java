package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.dto.job.JobResponse;
import com.TinasheGomo.JobPulse.entity.Job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobService {
    Job saveJob(Job job);
    Optional<Job> getJobBySourceAndExternalId(String source, String externalJobId);
    List<JobResponse> getAllJobs();
    JobResponse getJobById(UUID id);
}
