package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment;
import com.yuzhi.dts.platform.domain.modeling.DataStandardVersion;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardAttachmentDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardVersionDto;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

final class DataStandardMapper {

    private DataStandardMapper() {}

    static DataStandardDto toDto(DataStandard entity) {
        DataStandardDto dto = new DataStandardDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDomain(entity.getDomain());
        dto.setScope(entity.getScope());
        dto.setStatus(entity.getStatus());
        dto.setSecurityLevel(entity.getSecurityLevel());
        dto.setOwner(entity.getOwner());
        dto.setTags(splitTags(entity.getTags()));
        dto.setCurrentVersion(entity.getCurrentVersion());
        dto.setVersionNotes(entity.getVersionNotes());
        dto.setDescription(entity.getDescription());
        dto.setReviewCycle(entity.getReviewCycle());
        dto.setLastReviewAt(entity.getLastReviewAt());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        return dto;
    }

    static DataStandardVersionDto toDto(DataStandardVersion entity) {
        DataStandardVersionDto dto = new DataStandardVersionDto();
        dto.setId(entity.getId());
        dto.setVersion(entity.getVersion());
        dto.setStatus(entity.getStatus());
        dto.setChangeSummary(entity.getChangeSummary());
        dto.setSnapshotJson(entity.getSnapshotJson());
        dto.setReleasedAt(entity.getReleasedAt());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }

    static DataStandardAttachmentDto toDto(DataStandardAttachment entity) {
        DataStandardAttachmentDto dto = new DataStandardAttachmentDto();
        dto.setId(entity.getId());
        dto.setFileName(entity.getFileName());
        dto.setContentType(entity.getContentType());
        dto.setFileSize(entity.getFileSize());
        dto.setSha256(entity.getSha256());
        dto.setKeyVersion(entity.getKeyVersion());
        dto.setVersion(entity.getVersion());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }

    static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags
            .stream()
            .filter(Objects::nonNull)
            .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
            .filter(tag -> !tag.isBlank())
            .distinct()
            .collect(Collectors.joining(","));
    }

    static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays
            .stream(raw.split(","))
            .map(String::trim)
            .filter(tag -> !tag.isBlank())
            .collect(Collectors.toList());
    }
}

