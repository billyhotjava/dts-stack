package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.domain.PersonProfile;
import com.yuzhi.dts.admin.domain.enumeration.PersonLifecycleStatus;
import com.yuzhi.dts.admin.domain.enumeration.PersonSourceType;
import com.yuzhi.dts.admin.repository.PersonProfileRepository;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonnelProfileService {

    private final PersonProfileRepository profileRepository;

    public PersonnelProfileService(PersonProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public PersonProfile upsert(PersonnelPayload payload, Long batchId, PersonSourceType sourceType, String reference) {
        String naturalKey = resolveNaturalKey(payload);
        if (!StringUtils.isNotBlank(naturalKey)) {
            throw new PersonnelImportException("无法确定唯一人员编号，请提供 personCode/externalId/account 任一字段");
        }
        PersonProfile profile = resolveExisting(payload, naturalKey).orElseGet(() -> {
            PersonProfile created = new PersonProfile();
            created.setPersonCode(naturalKey);
            return created;
        });
        profile.setPersonCode(naturalKey);
        if (StringUtils.isNotBlank(payload.externalId())) {
            profile.setExternalId(payload.externalId());
        }
        if (StringUtils.isNotBlank(payload.account())) {
            profile.setAccount(payload.account());
        }
        if (StringUtils.isNotBlank(payload.fullName())) {
            profile.setFullName(payload.fullName());
        }
        if (StringUtils.isNotBlank(payload.nationalId())) {
            profile.setNationalId(payload.nationalId());
        }
        if (StringUtils.isNotBlank(payload.deptCode())) {
            profile.setDeptCode(payload.deptCode());
        }
        if (StringUtils.isNotBlank(payload.deptName())) {
            profile.setDeptName(payload.deptName());
        }
        if (StringUtils.isNotBlank(payload.deptPath())) {
            profile.setDeptPath(payload.deptPath());
        }
        if (StringUtils.isNotBlank(payload.title())) {
            profile.setTitle(payload.title());
        }
        if (StringUtils.isNotBlank(payload.grade())) {
            profile.setGrade(payload.grade());
        }
        if (StringUtils.isNotBlank(payload.email())) {
            profile.setEmail(payload.email());
        }
        if (StringUtils.isNotBlank(payload.phone())) {
            profile.setPhone(payload.phone());
        }
        if (StringUtils.isNotBlank(payload.status())) {
            profile.setLifecycleStatus(PersonLifecycleStatus.fromCode(payload.status()));
        }
        if (payload.activeFrom() != null) {
            profile.setActiveFrom(payload.activeFrom());
        }
        if (payload.activeTo() != null) {
            profile.setActiveTo(payload.activeTo());
        }
        profile.setAttributes(payload.safeAttributes());
        profile.setRawPayload(payload.safeAttributes());
        profile.setLastSourceType(sourceType);
        profile.setLastReference(reference);
        profile.setLastBatchId(batchId);
        profile.setLastSyncedAt(Instant.now());
        return profileRepository.save(profile);
    }

    private Optional<PersonProfile> resolveExisting(PersonnelPayload payload, String naturalKey) {
        if (StringUtils.isNotBlank(naturalKey)) {
            Optional<PersonProfile> byCode = profileRepository.findByPersonCodeIgnoreCase(naturalKey);
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        if (StringUtils.isNotBlank(payload.externalId())) {
            Optional<PersonProfile> found = profileRepository.findByExternalId(payload.externalId());
            if (found.isPresent()) {
                return found;
            }
        }
        if (StringUtils.isNotBlank(payload.account())) {
            Optional<PersonProfile> found = profileRepository.findByAccountIgnoreCase(payload.account());
            if (found.isPresent()) {
                return found;
            }
        }
        if (StringUtils.isNotBlank(payload.nationalId())) {
            Optional<PersonProfile> found = profileRepository.findByNationalId(payload.nationalId());
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private String resolveNaturalKey(PersonnelPayload payload) {
        if (StringUtils.isNotBlank(payload.personCode())) {
            return payload.personCode().trim();
        }
        if (StringUtils.isNotBlank(payload.externalId())) {
            return payload.externalId().trim();
        }
        if (StringUtils.isNotBlank(payload.account())) {
            return payload.account().trim();
        }
        if (StringUtils.isNotBlank(payload.nationalId())) {
            return payload.nationalId().trim();
        }
        return null;
    }
}
