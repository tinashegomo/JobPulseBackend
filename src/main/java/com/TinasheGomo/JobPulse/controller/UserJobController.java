package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.dto.userjob.UserJobResponse;
import com.TinasheGomo.JobPulse.security.AuthUser;
import com.TinasheGomo.JobPulse.service.UserJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user-jobs")
@RequiredArgsConstructor
public class UserJobController {

    private final UserJobService userJobService;

    @GetMapping("/me")
    public ResponseEntity<List<UserJobResponse>> getMyJobs(
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(userJobService.getUserJobsByUser(authUser.getUser()));
    }

    @GetMapping("/me/unnotified")
    public ResponseEntity<List<UserJobResponse>> getUnnotifiedJobs(
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(userJobService.getUnnotifiedJobsByUser(authUser.getUser()));
    }

    @PatchMapping("/{id}/seen")
    public ResponseEntity<Void> markSeen(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        userJobService.markSeen(authUser.getUser(), id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/unseen")
    public ResponseEntity<Void> markUnseen(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        userJobService.markUnseen(authUser.getUser(), id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/hide")
    public ResponseEntity<Void> hide(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        userJobService.hide(authUser.getUser(), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAllMyJobs(
            @AuthenticationPrincipal AuthUser authUser) {
        userJobService.deleteAllByUser(authUser.getUser());
        return ResponseEntity.ok().build();
    }
}