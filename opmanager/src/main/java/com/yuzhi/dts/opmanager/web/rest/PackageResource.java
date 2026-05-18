package com.yuzhi.dts.opmanager.web.rest;

import com.yuzhi.dts.opmanager.packageinfo.PackageRegistration;
import com.yuzhi.dts.opmanager.packageinfo.PackageRegistrationRequest;
import com.yuzhi.dts.opmanager.packageinfo.UploadResult;
import com.yuzhi.dts.opmanager.packageinfo.UpgradePackageService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/opmanager/packages")
public class PackageResource {

    private final UpgradePackageService packageService;

    public PackageResource(UpgradePackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    public List<PackageRegistration> list() {
        return packageService.list();
    }

    @PostMapping("/register-path")
    public PackageRegistration registerServerPath(@Valid @RequestBody PackageRegistrationRequest request) {
        return packageService.registerServerPath(Path.of(request.path()));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(@RequestParam("file") MultipartFile file) {
        return packageService.storeUploadedFile(file);
    }

    @GetMapping("/{id}")
    public PackageRegistration get(@PathVariable String id) {
        return packageService.find(id).orElseThrow(() -> new NoSuchElementException("package registration not found"));
    }
}
