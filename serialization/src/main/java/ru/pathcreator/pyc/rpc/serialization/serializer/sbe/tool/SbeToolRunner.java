package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.tool;

import uk.co.real_logic.sbe.SbeTool;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class SbeToolRunner {

    private SbeToolRunner() {
    }

    public static void generateJavaFromSchema(
            final Path schemaFile,
            final Path outputDirectory,
            final String targetNamespace
    ) {
        final Map<String, String> previous = new HashMap<>();
        set(previous, "sbe.generate.stubs", "true");
        set(previous, "sbe.target.language", "Java");
        set(previous, "sbe.output.dir", outputDirectory.toAbsolutePath().toString());
        set(previous, "sbe.target.namespace", targetNamespace);
        set(previous, "sbe.java.encoding.buffer.type", "org.agrona.MutableDirectBuffer");
        set(previous, "sbe.java.decoding.buffer.type", "org.agrona.DirectBuffer");
        try {
            SbeTool.main(new String[]{schemaFile.toAbsolutePath().toString()});
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to generate official SBE sources for " + schemaFile, e);
        } finally {
            restore(previous);
        }
    }

    private static void set(
            final Map<String, String> previous,
            final String key,
            final String value
    ) {
        previous.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }

    private static void restore(final Map<String, String> previous) {
        for (final Map.Entry<String, String> entry : previous.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}