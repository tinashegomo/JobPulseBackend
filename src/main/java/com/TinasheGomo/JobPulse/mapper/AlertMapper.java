package com.TinasheGomo.JobPulse.mapper;

import com.TinasheGomo.JobPulse.dto.alert.AlertRequest;
import com.TinasheGomo.JobPulse.dto.alert.AlertResponse;
import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AlertMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Alert toEntity(AlertRequest request);

    AlertResponse toResponse(Alert alert);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(AlertRequest request, @MappingTarget Alert alert);
}