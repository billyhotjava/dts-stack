package com.yuzhi.dts.opmanager.job;

import com.yuzhi.dts.opmanager.packageinfo.PackageRegistration;
import com.yuzhi.dts.opmanager.packageinfo.UpgradePackageService;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class UpgradePlanningService {

    private final UpgradePackageService packageService;
    private final FileJobStore jobStore;

    public UpgradePlanningService(UpgradePackageService packageService, FileJobStore jobStore) {
        this.packageService = packageService;
        this.jobStore = jobStore;
    }

    public UpgradeJob createPlan(String packageLookupId, String note) {
        PackageRegistration registration = resolveRegistration(packageLookupId);
        if (!registration.validation().valid()) {
            throw new IllegalArgumentException("package validation has errors; cannot create plan");
        }
        String message = note == null || note.isBlank() ? "dry-run plan created" : note;
        return jobStore.createPlanJob(
            registration.id(),
            registration.validation().packageId(),
            registration.validation().version(),
            message + " for " + registration.validation().packageId()
        );
    }

    private PackageRegistration resolveRegistration(String packageLookupId) {
        return packageService
            .find(packageLookupId)
            .or(
                () ->
                    packageService
                        .list()
                        .stream()
                        .filter(registration -> Objects.equals(registration.validation().packageId(), packageLookupId))
                        .findFirst()
            )
            .orElseThrow(() -> new NoSuchElementException("package registration not found"));
    }
}
