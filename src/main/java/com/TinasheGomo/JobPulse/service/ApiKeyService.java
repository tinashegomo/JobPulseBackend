package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.dto.apikey.ApiKeyRequest;
import com.TinasheGomo.JobPulse.dto.apikey.ApiKeyResponse;
import com.TinasheGomo.JobPulse.entity.User;

import java.util.List;
import java.util.UUID;

public interface ApiKeyService {
    ApiKeyResponse saveApiKey(ApiKeyRequest request, User user);
    List<ApiKeyResponse> getApiKeysByUser(User user);
    void deleteApiKey(UUID id, User user);
}