package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.alert.AlertRequest;
import com.TinasheGomo.JobPulse.dto.alert.AlertResponse;
import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.exception.exceptions.NotFoundException;
import com.TinasheGomo.JobPulse.mapper.AlertMapper;
import com.TinasheGomo.JobPulse.repository.AlertRepository;
import com.TinasheGomo.JobPulse.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;

    @Override
    public AlertResponse createAlert(AlertRequest request, User user) {
        Alert alert = alertMapper.toEntity(request);
        alert.setUser(user);
        if (alert.getActive() == null) {
            alert.setActive(true);
        }
        alertRepository.save(alert);
        return alertMapper.toResponse(alert);
    }

    @Override
    public List<AlertResponse> getAlertsByUser(User user) {
        return alertRepository.findByUser(user).stream()
                .map(alertMapper::toResponse)
                .toList();
    }

    @Override
    public AlertResponse getAlertById(UUID id, User user) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Alert not found"));
        if (!alert.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Alert not found");
        }
        return alertMapper.toResponse(alert);
    }

    @Override
    public AlertResponse updateAlert(UUID id, AlertRequest request, User user) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Alert not found"));
        if (!alert.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Alert not found");
        }
        alertMapper.updateEntityFromRequest(request, alert);
        alertRepository.save(alert);
        return alertMapper.toResponse(alert);
    }

    @Override
    public void deleteAlert(UUID id, User user) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Alert not found"));
        if (!alert.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Alert not found");
        }
        alertRepository.delete(alert);
    }

    @Override
    public List<Alert> getAllActiveAlerts() {
        return alertRepository.findByActiveTrue();
    }
}