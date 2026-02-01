package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingestion/files")
public class FileUploadResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final FileUploadService fileUploadService;

    public FileUploadResource(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public FileUploadService.FileUploadResult uploadFile(@RequestPart("file") MultipartFile file) {
        return fileUploadService.handleUpload(file);
    }
}
