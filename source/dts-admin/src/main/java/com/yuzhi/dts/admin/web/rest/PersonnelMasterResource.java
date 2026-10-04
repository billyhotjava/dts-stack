package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.domain.PersonImportBatch;
import com.yuzhi.dts.admin.domain.PersonProfile;
import com.yuzhi.dts.admin.repository.PersonImportBatchRepository;
import com.yuzhi.dts.admin.repository.PersonProfileRepository;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelImportResult;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.admin.service.personnel.PersonnelImportException;
import com.yuzhi.dts.admin.service.personnel.PersonnelImportService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.dto.personnel.ApiImportRequest;
import com.yuzhi.dts.admin.web.rest.dto.personnel.ManualImportRequest;
import com.yuzhi.dts.admin.web.rest.dto.personnel.PersonnelBatchView;
import com.yuzhi.dts.admin.web.rest.dto.personnel.PersonnelPayloadRequest;
import com.yuzhi.dts.admin.web.rest.dto.personnel.PersonnelProfileView;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/master-data/personnel")
public class PersonnelMasterResource {

    private final PersonnelImportService importService;
    private final PersonImportBatchRepository batchRepository;
    private final PersonProfileRepository profileRepository;

    public PersonnelMasterResource(
        PersonnelImportService importService,
        PersonImportBatchRepository batchRepository,
        PersonProfileRepository profileRepository
    ) {
        this.importService = importService;
        this.batchRepository = batchRepository;
        this.profileRepository = profileRepository;
    }

    @PostMapping("/import/api")
    public ResponseEntity<ApiResponse<PersonnelImportResult>> triggerApiImport(@Valid @RequestBody ApiImportRequest request) {
        PersonnelImportResult result = importService.importFromApi(request.reference(), request.dryRun(), request.cursor());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PersonnelImportResult>> importExcel(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun
    ) throws IOException {
        if (file.isEmpty()) {
            throw new PersonnelImportException("上传文件为空");
        }
        try (var inputStream = file.getInputStream()) {
            PersonnelImportResult result = importService.importFromExcel(file.getOriginalFilename(), inputStream, dryRun);
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
    }

    @PostMapping("/import/manual")
    public ResponseEntity<ApiResponse<PersonnelImportResult>> importManual(@Valid @RequestBody ManualImportRequest request) {
        List<PersonnelPayload> payloads = request.records().stream().map(PersonnelPayloadRequest::toPayload).collect(Collectors.toList());
        PersonnelImportResult result = importService.importManual(request.reference(), request.dryRun(), payloads);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<Page<PersonnelBatchView>>> listBatches(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<PersonImportBatch> batches = batchRepository.findAllByOrderByIdDesc(PageRequest.of(Math.max(page, 0), Math.min(size, 200)));
        Page<PersonnelBatchView> mapped = batches.map(this::toBatchView);
        return ResponseEntity.ok(ApiResponse.ok(mapped));
    }

    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<Page<PersonnelProfileView>>> listProfiles(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "deptCode", required = false) String deptCode
    ) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200));
        Page<PersonProfile> profiles;
        if (StringUtils.isNotBlank(deptCode)) {
            profiles = profileRepository.findByDeptCodeIgnoreCase(deptCode.trim(), pageable);
        } else {
            profiles = profileRepository.findAll(pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(profiles.map(this::toProfileView)));
    }

    @GetMapping("/profiles/{personCode}")
    public ResponseEntity<ApiResponse<PersonnelProfileView>> getProfile(@PathVariable String personCode) {
        return profileRepository
            .findByPersonCodeIgnoreCase(personCode.trim())
            .map(profile -> ResponseEntity.ok(ApiResponse.ok(toProfileView(profile))))
            .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("未找到人员：" + personCode)));
    }

    @ExceptionHandler(PersonnelImportException.class)
    public ResponseEntity<ApiResponse<Void>> handleImportError(PersonnelImportException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    private PersonnelBatchView toBatchView(PersonImportBatch batch) {
        return new PersonnelBatchView(
            batch.getId(),
            batch.getSourceType().name(),
            batch.getStatus().name(),
            batch.getTotalRecords(),
            batch.getSuccessRecords(),
            batch.getFailureRecords(),
            batch.getSkippedRecords(),
            batch.isDryRun(),
            batch.getReference(),
            batch.getStartedAt(),
            batch.getCompletedAt(),
            batch.getErrorMessage()
        );
    }

    private PersonnelProfileView toProfileView(PersonProfile profile) {
        return new PersonnelProfileView(
            profile.getId(),
            profile.getPersonCode(),
            profile.getExternalId(),
            profile.getAccount(),
            profile.getFullName(),
            profile.getDeptCode(),
            profile.getDeptName(),
            profile.getLifecycleStatus() == null ? null : profile.getLifecycleStatus().name(),
            profile.getEmail(),
            profile.getPhone(),
            profile.getLastSyncedAt()
        );
    }
}
