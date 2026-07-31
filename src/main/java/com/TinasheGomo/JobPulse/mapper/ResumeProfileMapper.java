package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileRequest;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.entity.ResumeProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ResumeProfileMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ResumeProfile toEntity(ResumeProfileRequest request);

    ResumeProfileResponse toResponse(ResumeProfile profile);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(ResumeProfileRequest request, @MappingTarget ResumeProfile profile);
}