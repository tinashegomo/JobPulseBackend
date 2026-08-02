package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.userjob.UserJobResponse;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.entity.UserJob;
import com.TinasheGomo.JobPulse.exception.exceptions.NotFoundException;
import com.TinasheGomo.JobPulse.mapper.UserJobMapper;
import com.TinasheGomo.JobPulse.repository.JobRepository;
import com.TinasheGomo.JobPulse.repository.UserJobRepository;
import com.TinasheGomo.JobPulse.service.UserJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserJobServiceImpl implements UserJobService {

    private final UserJobRepository userJobRepository;
    private final JobRepository jobRepository;
    private final UserJobMapper userJobMapper;

    @Override
    public UserJobResponse saveUserJob(User user, Job job, Integer score) {
        UserJob userJob = UserJob.builder()
                .user(user)
                .job(job)
                .score(score)
                .build();
        userJobRepository.save(userJob);
        return userJobMapper.toResponse(userJob);
    }

    @Override
    public List<UserJobResponse> getUserJobsByUser(User user) {
        return userJobRepository.findByUserAndHiddenFalseOrderByScoreDesc(user).stream()
                .map(userJobMapper::toResponse)
                .toList();
    }

    @Override
    public List<UserJobResponse> getUnnotifiedJobsByUser(User user) {
        return userJobRepository.findByUserAndNotifiedAtIsNullOrderByScoreDesc(user).stream()
                .map(userJobMapper::toResponse)
                .toList();
    }

    @Override
    public void markSeen(User user, UUID userJobId) {
        UserJob userJob = userJobRepository.findById(userJobId)
                .filter(uj -> uj.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("UserJob not found"));
        userJob.setSeen(true);
        userJobRepository.save(userJob);
    }

    @Override
    public void markUnseen(User user, UUID userJobId) {
        UserJob userJob = userJobRepository.findById(userJobId)
                .filter(uj -> uj.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("UserJob not found"));
        userJob.setSeen(false);
        userJobRepository.save(userJob);
    }

    @Override
    public void hide(User user, UUID userJobId) {
        UserJob userJob = userJobRepository.findById(userJobId)
                .filter(uj -> uj.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("UserJob not found"));
        userJob.setHidden(true);
        userJobRepository.save(userJob);
    }

    @Override
    @Transactional
    public void deleteAllByUser(User user) {
        userJobRepository.deleteAllByUser(user);
        jobRepository.deleteOrphanedJobs();
    }
}