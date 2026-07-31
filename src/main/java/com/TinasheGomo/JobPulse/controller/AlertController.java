package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.dto.alert.AlertRequest;
import com.TinasheGomo.JobPulse.dto.alert.AlertResponse;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.security.AuthUser;
import com.TinasheGomo.JobPulse.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(
            @Valid @RequestBody AlertRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(alertService.createAlert(request, authUser.getUser()));
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(alertService.getAlertsByUser(authUser.getUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlertById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(alertService.getAlertById(id, authUser.getUser()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertResponse> updateAlert(
            @PathVariable UUID id,
            @Valid @RequestBody AlertRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(alertService.updateAlert(id, request, authUser.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        alertService.deleteAlert(id, authUser.getUser());
        return ResponseEntity.noContent().build();
    }
}