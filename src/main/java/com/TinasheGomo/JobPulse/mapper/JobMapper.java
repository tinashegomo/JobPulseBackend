package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.job.JobResponse;
import com.TinasheGomo.JobPulse.entity.Job;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface JobMapper {

    JobResponse toResponse(Job job);
}