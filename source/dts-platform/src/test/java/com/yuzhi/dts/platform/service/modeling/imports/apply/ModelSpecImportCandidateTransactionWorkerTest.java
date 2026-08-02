package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ArtifactType;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportResult;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportedArtifact;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.NodeKind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyCommandCodec.DecodedCandidate;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecImportCandidateTransactionWorkerTest {

    @Test
    void usesImportedDbtWriterAndArtifactsForDesignerRepresentableExternalSql() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelLifecycleService lifecycle = mock(ModelLifecycleService.class);
        ModelingDbtArtifactImportService artifactImports = mock(ModelingDbtArtifactImportService.class);
        ModelSpecImportApplyCommandCodec commandCodec = mock(ModelSpecImportApplyCommandCodec.class);
        ModelSpecImportApplyRepository repository = mock(ModelSpecImportApplyRepository.class);
        ModelSpecImportCandidateTransactionWorker worker = new ModelSpecImportCandidateTransactionWorker(
            modelSpecs,
            lifecycle,
            artifactImports,
            commandCodec,
            repository
        );
        UUID modelId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID implementationId = UUID.randomUUID();
        String modelChecksum = "a".repeat(64);
        String implementationChecksum = "b".repeat(64);
        Candidate candidate = candidate(modelId, modelChecksum, implementationChecksum);
        CreateModelSpecCommand create = mock(CreateModelSpecCommand.class);
        UpdateModelSpecCommand update = mock(UpdateModelSpecCommand.class);
        SaveImplementationCommand implementationCommand = mock(SaveImplementationCommand.class);
        when(implementationCommand.ownership()).thenReturn(ImplementationMode.DBT_MANAGED);
        String sql = "select 1 as simple_key";
        ImportedArtifact artifact = new ImportedArtifact(
            candidate.dbtUniqueId(),
            NodeKind.MODEL,
            ArtifactType.SQL,
            "models/simple.sql",
            ModelPackageChecksum.sha256Text(sql),
            sql,
            "view"
        );
        when(commandCodec.decode(eq(candidate), eq("{}"), eq(Set.of()))).thenReturn(
            new DecodedCandidate(create, update, implementationCommand, "pjm", List.of(artifact))
        );
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(modelId);
        when(model.planId()).thenReturn(planId);
        when(model.revision()).thenReturn(1);
        when(model.checksum()).thenReturn(modelChecksum);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(modelSpecs.createImported("tenant", "actor", modelId, create)).thenReturn(new CreateResult(model, false));
        ImplementationView implementation = mock(ImplementationView.class);
        when(implementation.id()).thenReturn(implementationId);
        when(implementation.implementationRevision()).thenReturn(1);
        when(implementation.implementationChecksum()).thenReturn(implementationChecksum);
        when(
            lifecycle.saveImportedDbtImplementation(
                eq("tenant"),
                eq("actor"),
                eq(modelId),
                any(),
                eq(ModelStatus.DRAFT),
                any(),
                eq("pjm"),
                eq(candidate.dbtUniqueId()),
                eq(implementationCommand)
            )
        ).thenReturn(implementation);
        when(artifactImports.importArtifacts(any())).thenReturn(
            new ImportResult(
                modelId,
                planId,
                1,
                modelChecksum,
                implementationId,
                1,
                implementationChecksum,
                1
            )
        );
        when(repository.recordSuccess(any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(4));

        CandidateResult result = worker.execute(
            new ModelSpecImportCandidateTransactionWorker.Execution(
                "tenant",
                "actor",
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                candidate,
                "{}",
                "candidate-key",
                "candidate-hash",
                "owner-token",
                Set.of()
            )
        );

        assertThat(result.status()).isEqualTo(ResultStatus.CREATED);
        assertThat(result.artifactCount()).isEqualTo(1);
        verify(lifecycle).saveImportedDbtImplementation(
            eq("tenant"),
            eq("actor"),
            eq(modelId),
            any(),
            eq(ModelStatus.DRAFT),
            any(),
            eq("pjm"),
            eq(candidate.dbtUniqueId()),
            eq(implementationCommand)
        );
        verify(artifactImports).importArtifacts(any());
    }

    private static Candidate candidate(UUID modelId, String modelChecksum, String implementationChecksum) {
        return new Candidate(
            "model.pjm.simple",
            modelId,
            1,
            1,
            0,
            null,
            "DRAFT",
            0,
            null,
            modelChecksum,
            implementationChecksum,
            "CREATE",
            "DESIGNER_GENERATED",
            "{}",
            "{}",
            "{}",
            "{}",
            "{}",
            "{}"
        );
    }
}
