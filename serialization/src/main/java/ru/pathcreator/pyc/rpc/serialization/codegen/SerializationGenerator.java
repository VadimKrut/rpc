package ru.pathcreator.pyc.rpc.serialization.codegen;

import ru.pathcreator.pyc.rpc.codec.SerializationFormat;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GenerationResult;

import java.util.List;

public interface SerializationGenerator {

    SerializationFormat format();

    GenerationResult generate(List<Class<?>> rootTypes, String generatedPackage);
}