package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DbtImplementationDraftContractTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void normalizesProjectRelativePathsAndRejectsTraversalOrAbsolutePaths() {
        List<FileInput> normalized = DbtImplementationDraftContract.normalizeFiles(
            List.of(
                new FileInput("./models\\orders.sql", "select 1\r\n"),
                new FileInput("dbt_project.yml", "name: sprint83\n")
            )
        );

        assertThat(normalized).extracting(FileInput::path).containsExactly("dbt_project.yml", "models/orders.sql");
        assertThat(normalized.get(1).content()).isEqualTo("select 1\n");

        assertThatThrownBy(() -> DbtImplementationDraftContract.normalizeFiles(List.of(new FileInput("../secret.sql", "x"))))
            .isInstanceOf(DraftException.class)
            .hasMessageContaining("project-relative");
        assertThatThrownBy(() -> DbtImplementationDraftContract.normalizeFiles(List.of(new FileInput("/tmp/model.sql", "x"))))
            .isInstanceOf(DraftException.class);
        assertThatThrownBy(() -> DbtImplementationDraftContract.normalizeFiles(List.of(new FileInput("C:\\tmp\\model.sql", "x"))))
            .isInstanceOf(DraftException.class);
    }

    @Test
    void enforcesFileCountPerFileAndAggregateBudgets() {
        List<FileInput> tooMany = new ArrayList<>();
        for (int index = 0; index <= DbtImplementationDraftContract.MAX_FILES; index++) {
            tooMany.add(new FileInput("models/m" + index + ".sql", "select 1"));
        }
        assertThatThrownBy(() -> DbtImplementationDraftContract.normalizeFiles(tooMany))
            .isInstanceOf(DraftException.class)
            .hasMessageContaining("128");

        String oversized = "x".repeat(DbtImplementationDraftContract.MAX_FILE_BYTES + 1);
        assertThatThrownBy(() ->
            DbtImplementationDraftContract.normalizeFiles(List.of(new FileInput("models/large.sql", oversized)))
        )
            .isInstanceOf(DraftException.class)
            .hasMessageContaining("2 MiB");

        String twoMiB = "x".repeat(DbtImplementationDraftContract.MAX_FILE_BYTES);
        List<FileInput> overTotal = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            overTotal.add(new FileInput("models/part" + index + ".sql", twoMiB));
        }
        assertThatThrownBy(() -> DbtImplementationDraftContract.normalizeFiles(overTotal))
            .isInstanceOf(DraftException.class)
            .hasMessageContaining("16 MiB");
    }

    @Test
    void beanValidationRejectsBlankCheckpointPinsAndInvalidNestedFilesBeforeServiceDispatch() {
        SaveFilesRequest request = new SaveFilesRequest(" ", List.of(new FileInput(" ", null)));

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("expectedEtag", "files[0].path", "files[0].content");
    }

    @Test
    void beanValidationCharacterCapsDoNotReplaceExactUtf8ByteLimits() {
        String multiByte = "汉".repeat((DbtImplementationDraftContract.MAX_FILE_BYTES / 3) + 1);
        FileInput file = new FileInput("models/utf8.sql", multiByte);

        assertThat(validator.validate(new SaveFilesRequest("etag", List.of(file)))).isEmpty();
        assertThatThrownBy(() -> DbtImplementationDraftContract.normalizeFiles(List.of(file)))
            .isInstanceOf(DraftException.class)
            .hasMessageContaining("2 MiB");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "profiles.yml",
            "config/profiles.yaml",
            ".env",
            ".env.production",
            "keys/client-private.key",
            "certs/client.p12",
            ".aws/credentials",
            "config/client_secret.json",
            "config/access-token.txt",
        }
    )
    void rejectsSensitiveProjectFilesBeforeTheirContentCanReachPersistence(String path) {
        DraftException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> DbtImplementationDraftContract.normalizeFiles(List.of(new FileInput(path, "credential-body"))),
            DraftException.class
        );

        assertThat(failure).isNotNull();
        assertThat(failure.code()).isEqualTo("DBT_DRAFT_SENSITIVE_FILE_FORBIDDEN");
        assertThat(failure.getMessage()).doesNotContain(path, "credential-body");
    }
}
