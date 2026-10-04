package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
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
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DimensionAttributeBlueprint;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DimensionDefinitionBlueprint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class ModelSpecImportCandidateTransactionWorkerTest {

    @Test
    void skipsPublishedModelWhenFrozenPinsMatch() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelLifecycleService lifecycle = mock(ModelLifecycleService.class);
        ModelingDbtArtifactImportService artifactImports = mock(ModelingDbtArtifactImportService.class);
        ModelSpecImportApplyCommandCodec commandCodec = mock(ModelSpecImportApplyCommandCodec.class);
        ModelSpecImportApplyRepository repository = mock(ModelSpecImportApplyRepository.class);
        DimensionDefinitionApplicationService dimensionDefinitions = mock(DimensionDefinitionApplicationService.class);
        ModelSpecImportCandidateTransactionWorker worker = new ModelSpecImportCandidateTransactionWorker(
            modelSpecs,
            lifecycle,
            artifactImports,
            commandCodec,
            repository,
            dimensionDefinitions
        );
        UUID modelId = UUID.randomUUID();
        String modelChecksum = "a".repeat(64);
        String implementationChecksum = "b".repeat(64);
        Candidate candidate = new Candidate(
            "model.pjm.published_dimension",
            modelId,
            2,
            1,
            2,
            modelChecksum,
            "PUBLISHED",
            1,
            implementationChecksum,
            modelChecksum,
            implementationChecksum,
            "SKIP",
            "DBT_BACKED",
            "{}",
            "{}",
            "{}",
            "{}",
            "{}",
            "{}"
        );
        SaveImplementationCommand implementationCommand = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("dbtUniqueId", candidate.dbtUniqueId()))),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "stable-model-import-key:impl"
        );
        when(commandCodec.decode(eq(candidate), eq("{}"), eq(Set.of()))).thenReturn(
            new DecodedCandidate(
                mock(CreateModelSpecCommand.class),
                mock(UpdateModelSpecCommand.class),
                implementationCommand,
                "pjm",
                List.of()
            )
        );
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(modelId);
        when(model.revision()).thenReturn(2);
        when(model.checksum()).thenReturn(modelChecksum);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(modelSpecs.get("tenant", modelId)).thenReturn(model);
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

        assertThat(result.status()).isEqualTo(ResultStatus.SKIPPED);
        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.modelChecksum()).isEqualTo(modelChecksum);
        assertThat(result.implementationRevision()).isEqualTo(1);
        assertThat(result.implementationChecksum()).isEqualTo(implementationChecksum);
    }

    @Test
    void usesImportedDbtWriterAndArtifactsForDesignerRepresentableExternalSql() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelLifecycleService lifecycle = mock(ModelLifecycleService.class);
        ModelingDbtArtifactImportService artifactImports = mock(ModelingDbtArtifactImportService.class);
        ModelSpecImportApplyCommandCodec commandCodec = mock(ModelSpecImportApplyCommandCodec.class);
        ModelSpecImportApplyRepository repository = mock(ModelSpecImportApplyRepository.class);
        DimensionDefinitionApplicationService dimensionDefinitions = mock(DimensionDefinitionApplicationService.class);
        ModelSpecImportCandidateTransactionWorker worker = new ModelSpecImportCandidateTransactionWorker(
            modelSpecs,
            lifecycle,
            artifactImports,
            commandCodec,
            repository,
            dimensionDefinitions
        );
        UUID modelId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID implementationId = UUID.randomUUID();
        String modelChecksum = "a".repeat(64);
        String implementationChecksum = "b".repeat(64);
        Candidate candidate = candidate(modelId, modelChecksum, implementationChecksum);
        CreateModelSpecCommand create = mock(CreateModelSpecCommand.class);
        UpdateModelSpecCommand update = mock(UpdateModelSpecCommand.class);
        UUID definitionId = UUID.fromString("70000000-0000-0000-0000-000000000070");
        UUID domainId = UUID.randomUUID();
        DimensionDefinitionRef definitionRef = new DimensionDefinitionRef(definitionId, 2);
        DimensionDefinitionBlueprint definitionBlueprint = new DimensionDefinitionBlueprint(
            "测试状态维度",
            "测试状态",
            "用于验证可移植导入契约的中性状态维度。",
            List.of(
                new DimensionAttributeBlueprint(
                    "STATUS_CODE",
                    "状态编码",
                    "跨系统稳定的状态编码。",
                    true,
                    null,
                    null,
                    1
                )
            )
        );
        when(create.domainId()).thenReturn(domainId);
        when(create.dimensionDefinitionRef()).thenReturn(definitionRef);
        SaveImplementationCommand implementationCommand = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("dbtUniqueId", candidate.dbtUniqueId()))),
            List.of(),
            Map.of("targetPhysicalName", "simple", "loadStrategy", "FULL", "partitionFields", List.of()),
            ImplementationMode.DBT_MANAGED,
            "view",
            "stable-model-import-key:impl"
        );
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
            new DecodedCandidate(create, update, implementationCommand, "pjm", List.of(artifact), definitionBlueprint)
        );
        View importedDefinition = mock(View.class);
        when(importedDefinition.id()).thenReturn(definitionId);
        when(importedDefinition.revision()).thenReturn(2);
        when(
            dimensionDefinitions.importCurrent(
                eq("tenant"),
                eq("actor"),
                eq(definitionId),
                eq("dim_70000000000000000000000000000070"),
                any()
            )
        ).thenReturn(importedDefinition);
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
                any()
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
        ArgumentCaptor<SaveImplementationCommand> implementationCaptor = ArgumentCaptor.forClass(
            SaveImplementationCommand.class
        );
        verify(lifecycle).saveImportedDbtImplementation(
            eq("tenant"),
            eq("actor"),
            eq(modelId),
            any(),
            eq(ModelStatus.DRAFT),
            any(),
            eq("pjm"),
            eq(candidate.dbtUniqueId()),
            implementationCaptor.capture()
        );
        assertThat(implementationCaptor.getValue())
            .usingRecursiveComparison()
            .ignoringFields("idempotencyKey")
            .isEqualTo(implementationCommand);
        assertThat(implementationCaptor.getValue().idempotencyKey()).isEqualTo("candidate-key:implementation");
        verify(dimensionDefinitions).importCurrent(
            eq("tenant"),
            eq("actor"),
            eq(definitionId),
            eq("dim_70000000000000000000000000000070"),
            any()
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
