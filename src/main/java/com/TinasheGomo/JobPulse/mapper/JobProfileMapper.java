package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.jobprofile.JobProfileResponse;
import com.TinasheGomo.JobPulse.entity.JobProfile;
import org.mapstruct.*;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface JobProfileMapper {

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "requiredSkills", expression = "java(splitSkills(jobProfile.getRequiredSkills()))")
    @Mapping(target = "bonusSkills", expression = "java(splitSkills(jobProfile.getBonusSkills()))")
    JobProfileResponse toResponse(JobProfile jobProfile);

    default List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) return List.of();
        return Arrays.asList(skills.split(",\\s*"));
    }
}
