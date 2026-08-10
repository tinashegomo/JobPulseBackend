package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.dto.userjob.UserJobResponse;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.entity.User;

import java.util.List;
import java.util.UUID;

public interface UserJobService {
    UserJobResponse saveUserJob(User user, Job job, Integer score);
    List<UserJobResponse> getUserJobsByUser(User user);
    List<UserJobResponse> getUnnotifiedJobsByUser(User user);
    void markSeen(User user, UUID userJobId);
    void markUnseen(User user, UUID userJobId);
    void hide(User user, UUID userJobId);
    void deleteAllByUser(User user);
    boolean userHasJob(UUID userId, String externalJobId, String source);
}