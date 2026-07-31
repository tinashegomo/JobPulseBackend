package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.dto.alert.AlertRequest;
import com.TinasheGomo.JobPulse.dto.alert.AlertResponse;
import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.User;

import java.util.List;
import java.util.UUID;

public interface AlertService {
    AlertResponse createAlert(AlertRequest request, User user);
    List<AlertResponse> getAlertsByUser(User user);
    AlertResponse getAlertById(UUID id, User user);
    AlertResponse updateAlert(UUID id, AlertRequest request, User user);
    void deleteAlert(UUID id, User user);
    List<Alert> getAllActiveAlerts();
}