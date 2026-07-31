package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.keyword.KeywordResponse;
import com.TinasheGomo.JobPulse.entity.Keyword;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KeywordMapper {
    KeywordResponse toResponse(Keyword keyword);
}
