package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.apikey.ApiKeyResponse;
import com.TinasheGomo.JobPulse.entity.ApiKey;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper {

    @Mapping(target = "maskedKey", expression = "java(maskKey(apiKey.getEncryptedKey()))")
    ApiKeyResponse toResponse(ApiKey apiKey);

    default String maskKey(String encryptedKey) {
        if (encryptedKey == null || encryptedKey.length() < 8) {
            return "****";
        }
        return encryptedKey.substring(0, 4) + "****" + encryptedKey.substring(encryptedKey.length() - 4);
    }
}