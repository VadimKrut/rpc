package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeAnalysisResult;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeAnalysisStatus;

import java.util.List;

public final class SbeCodecFactoryGenerator {

    public String generateFactory(
            final String packageName,
            final List<SbeAnalysisResult> results
    ) {
        final StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import ru.pathcreator.pyc.rpc.codec.SerializationCodec;\n");
        source.append("import ru.pathcreator.pyc.rpc.codec.SerializationCodecFactory;\n");
        source.append("import java.util.List;\n\n");
        source.append("public final class SerializationCodecFactoryImpl implements SerializationCodecFactory {\n");
        source.append("    @Override\n");
        source.append("    public List<SerializationCodec<?>> codecs() {\n");
        source.append("        return List.of(\n");
        boolean first = true;
        for (final SbeAnalysisResult result : results) {
            if (result.strategy() == SbeAnalysisStatus.FAIL) {
                continue;
            }
            if (!first) {
                source.append(",\n");
            }
            first = false;
            source.append("                new ").append(packageName).append(".").append(SbeNaming.codecSimpleName(result.rootType())).append("()");
        }
        source.append("\n        );\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }
}