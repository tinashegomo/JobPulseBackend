package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileRequest;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.entity.ResumeProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface ResumeProfileMapper {

    ObjectMapper objectMapper = new ObjectMapper();

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ResumeProfile toEntity(ResumeProfileRequest request);

    @Mapping(target = "profile", ignore = true)
    ResumeProfileResponse toResponse(ResumeProfile profile);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(ResumeProfileRequest request, @MappingTarget ResumeProfile profile);

    @AfterMapping
    default void mapProfileJson(ResumeProfile source, @MappingTarget ResumeProfileResponse target) {
        if (source.getProfile() != null && !source.getProfile().isBlank()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(
                        source.getProfile(), new TypeReference<Map<String, Object>>() {});
                target.setProfile(parsed);
            } catch (Exception e) {
                target.setProfile(source.getProfile());
            }
        }
    }
}