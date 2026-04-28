package ru.pathcreator.pyc.rpc.serialization.codegen.artifact;

import java.util.List;

public record GenerationResult(
        List<GeneratedJavaSource> javaSources,
        List<GeneratedResource> resources
) {
}