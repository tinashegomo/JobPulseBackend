package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.job.JobResponse;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.exception.exceptions.NotFoundException;
import com.TinasheGomo.JobPulse.mapper.JobMapper;
import com.TinasheGomo.JobPulse.repository.JobRepository;
import com.TinasheGomo.JobPulse.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;

    @Override
    public Job saveJob(Job job) {
        return jobRepository.save(job);
    }

    @Override
    public Optional<Job> getJobBySourceAndExternalId(String source, String externalJobId) {
        return jobRepository.findBySourceAndExternalJobId(source, externalJobId);
    }

    @Override
    public List<JobResponse> getAllJobs() {
        return jobRepository.findAll().stream()
                .map(jobMapper::toResponse)
                .toList();
    }

    @Override
    public JobResponse getJobById(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Job not found"));
        return jobMapper.toResponse(job);
    }
}