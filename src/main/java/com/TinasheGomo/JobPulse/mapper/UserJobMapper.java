package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.userjob.UserJobResponse;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.entity.UserJob;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserJobMapper {

    @Mapping(source = "job.id", target = "jobId")
    @Mapping(source = "job.source", target = "source")
    @Mapping(source = "job.externalJobId", target = "externalJobId")
    @Mapping(source = "job.title", target = "title")
    @Mapping(source = "job.company", target = "company")
    @Mapping(source = "job.location", target = "location")
    @Mapping(source = "job.url", target = "url")
    @Mapping(source = "job.description", target = "description")
    @Mapping(source = "job.postedAt", target = "postedAt")
    UserJobResponse toResponse(UserJob userJob);
}