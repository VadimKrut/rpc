package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen;

import ru.pathcreator.pyc.rpc.codec.SerializationFormat;
import ru.pathcreator.pyc.rpc.serialization.codegen.SerializationGenerator;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GeneratedJavaSource;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GeneratedResource;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GenerationResult;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.introspection.SbeCompatibilityPlanner;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeAnalysisResult;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeAnalysisStatus;

import java.util.ArrayList;
import java.util.List;

public final class SbeGenerationEngine implements SerializationGenerator {

    private final SbeSchemaGenerator schemaGenerator = new SbeSchemaGenerator();
    private final SbeAdapterGenerator sbeAdapterGenerator = new SbeAdapterGenerator();
    private final SbeCompatibilityPlanner planner = new SbeCompatibilityPlanner();
    private final SbeCodecFactoryGenerator registryGenerator = new SbeCodecFactoryGenerator();

    @Override
    public SerializationFormat format() {
        return SerializationFormat.SBE;
    }

    @Override
    public GenerationResult generate(final List<Class<?>> rootTypes, final String generatedPackage) {
        final List<SbeAnalysisResult> results = new ArrayList<>();
        final List<GeneratedJavaSource> javaSources = new ArrayList<>();
        final List<GeneratedResource> resources = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        for (final Class<?> rootType : rootTypes) {
            final SbeAnalysisResult result = planner.plan(rootType);
            results.add(result);
            if (result.strategy() == SbeAnalysisStatus.FAIL) {
                failures.add(rootType.getName() + " -> " + String.join("; ", result.problems()));
                continue;
            }
            final String codecSource = sbeAdapterGenerator.generate(result.rootSpec(), generatedPackage);
            javaSources.add(new GeneratedJavaSource(generatedPackage + "." + SbeNaming.codecSimpleName(rootType), codecSource));
            resources.add(new GeneratedResource(
                    "META-INF/rpc-serialization/sbe/" + rootType.getName().replace('.', '/') + ".xml",
                    schemaGenerator.generate(result.rootSpec())
            ));
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("RPC serialization generation failed:\n" + String.join("\n", failures));
        }
        javaSources.add(new GeneratedJavaSource(
                generatedPackage + ".SerializationCodecFactoryImpl",
                registryGenerator.generateFactory(generatedPackage, results)
        ));
        resources.add(new GeneratedResource(
                "META-INF/services/ru.pathcreator.pyc.rpc.codec.SerializationCodecFactory",
                generatedPackage + ".SerializationCodecFactoryImpl\n"
        ));
        return new GenerationResult(javaSources, resources);
    }
}