package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reuses the existing deterministic dbt compiler without creating another ModelSpec. */
@Component
public class CanonicalModelLifecycleCompilerAdapter implements ModelLifecycleCompilerPort {

    private final ModelSpecApplicationService modelSpecs;

    public CanonicalModelLifecycleCompilerAdapter(ModelSpecApplicationService modelSpecs) {
        this.modelSpecs = modelSpecs;
    }

    @Override
    public List<ArtifactWrite> compile(String tenantId, ModelSpecView model) {
        ModelingVNextContract.ModelSpec projection = ModelSpecCompilerProjection.project(
            model,
            reference -> modelSpecs.revision(tenantId, reference)
        );
        ModelingDbtCompiler.CompiledArtifacts compiled = ModelingDbtCompiler.compile(projection);
        List<ArtifactWrite> result = new ArrayList<>();
        for (Map.Entry<String, String> file : compiled.files().entrySet()) {
            result.add(
                new ArtifactWrite(
                    artifactType(file.getKey()),
                    compiled.outputDirectory() + "/" + file.getKey(),
                    checksum(file.getValue()),
                    file.getValue()
                )
            );
        }
        return List.copyOf(result);
    }

    private static String artifactType(String path) {
        if (path.endsWith(".tests.yml")) return "TEST";
        if (path.endsWith(".yml") || path.endsWith(".yaml")) return "SCHEMA";
        if (path.endsWith(".sql")) return "SQL";
        return "DOC";
    }

    private static String checksum(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
