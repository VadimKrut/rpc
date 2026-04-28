package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model;

import java.util.List;

public record SbeAnalysisResult(
        Class<?> rootType,
        SbeTypeSpec rootSpec,
        SbeAnalysisStatus strategy,
        List<String> problems
) {
}