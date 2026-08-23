package com.yuzhi.dts.platform.service.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class WordRootContract {

    private WordRootContract() {}

    public record UpsertRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$") String code,
        @NotBlank @Size(max = 128) String nameCn,
        @NotBlank @Size(max = 256) String nameEn,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$") String abbreviation,
        @Size(max = 128) String domain,
        @Size(max = 32) String version
    ) {}

    public record View(
        UUID id,
        String code,
        String nameCn,
        String nameEn,
        String abbreviation,
        String domain,
        String version,
        String status,
        String ownerDept
    ) {}
}
