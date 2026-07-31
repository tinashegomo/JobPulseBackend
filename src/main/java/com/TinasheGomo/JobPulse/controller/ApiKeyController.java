package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.dto.apikey.ApiKeyRequest;
import com.TinasheGomo.JobPulse.dto.apikey.ApiKeyResponse;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.security.AuthUser;
import com.TinasheGomo.JobPulse.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<ApiKeyResponse> saveApiKey(
            @Valid @RequestBody ApiKeyRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(apiKeyService.saveApiKey(request, authUser.getUser()));
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> getApiKeys(
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(apiKeyService.getApiKeysByUser(authUser.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiKey(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        apiKeyService.deleteApiKey(id, authUser.getUser());
        return ResponseEntity.noContent().build();
    }
}