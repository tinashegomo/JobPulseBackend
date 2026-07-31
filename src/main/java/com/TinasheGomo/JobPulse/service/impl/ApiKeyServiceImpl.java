package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.apikey.ApiKeyRequest;
import com.TinasheGomo.JobPulse.dto.apikey.ApiKeyResponse;
import com.TinasheGomo.JobPulse.entity.ApiKey;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.exception.exceptions.NotFoundException;
import com.TinasheGomo.JobPulse.mapper.ApiKeyMapper;
import com.TinasheGomo.JobPulse.repository.ApiKeyRepository;
import com.TinasheGomo.JobPulse.service.ApiKeyService;
import com.TinasheGomo.JobPulse.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyMapper apiKeyMapper;
    private final EncryptionUtil encryptionUtil;

    @Override
    public ApiKeyResponse saveApiKey(ApiKeyRequest request, User user) {
        ApiKey apiKey = apiKeyRepository.findByUserAndProviderAndActiveTrue(user, request.getProvider())
                .orElseGet(() -> ApiKey.builder()
                        .user(user)
                        .provider(request.getProvider())
                        .active(true)
                        .build());

        apiKey.setEncryptedKey(encryptionUtil.encrypt(request.getApiKey()));
        apiKeyRepository.save(apiKey);
        return apiKeyMapper.toResponse(apiKey);
    }

    @Override
    public List<ApiKeyResponse> getApiKeysByUser(User user) {
        return apiKeyRepository.findByUserAndActiveTrue(user).stream()
                .map(apiKeyMapper::toResponse)
                .toList();
    }

    @Override
    public void deleteApiKey(UUID id, User user) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("API Key not found"));
        if (!apiKey.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("API Key not found");
        }
        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
    }
}