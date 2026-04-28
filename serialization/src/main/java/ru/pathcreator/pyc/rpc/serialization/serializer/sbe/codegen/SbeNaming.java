package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeFieldSpec;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeTypeSpec;

import java.util.Locale;

public final class SbeNaming {

    private SbeNaming() {
    }

    public static String codecSimpleName(final Class<?> type) {
        return sanitize(type.getName()) + "SerializationCodec";
    }

    public static String officialPackageName(final String generatedPackage, final Class<?> rootType) {
        return generatedPackage + ".sbe." + packageToken(rootType);
    }

    public static String adapterQualifiedClassName(final String generatedPackage, final Class<?> rootType) {
        return generatedPackage + "." + codecSimpleName(rootType);
    }

    public static String packageToken(final Class<?> type) {
        return sanitize(type.getName()).toLowerCase(Locale.ROOT);
    }

    public static String messageEncoderSimpleName(final SbeTypeSpec spec) {
        return spec.schemaName() + "Encoder";
    }

    public static String messageDecoderSimpleName(final SbeTypeSpec spec) {
        return spec.schemaName() + "Decoder";
    }

    public static String schemaFieldName(final SbeFieldSpec field) {
        return schemaFieldName(field.name());
    }

    public static String schemaFieldName(final String rawFieldName) {
        return "rpc" + Character.toUpperCase(rawFieldName.charAt(0)) + rawFieldName.substring(1);
    }

    public static String sanitize(final String value) {
        return value.replace('.', '_').replace('$', '_');
    }

    public static String javaTypeName(final Class<?> type) {
        if (type.isArray()) {
            return javaTypeName(type.getComponentType()) + "[]";
        }
        final String canonicalName = type.getCanonicalName();
        if (canonicalName != null) {
            return canonicalName;
        }
        return type.getName().replace('$', '.');
    }
}