package ru.pathcreator.pyc.rpc.serialization.debug.support;

import org.agrona.concurrent.UnsafeBuffer;
import ru.pathcreator.pyc.rpc.codec.SerializationCodec;
import ru.pathcreator.pyc.rpc.codec.SerializationCodecFactory;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GeneratedJavaSource;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GeneratedResource;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GenerationResult;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen.SbeGenerationEngine;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen.SbeNaming;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.tool.SbeToolRunner;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class DebugRoundTripRuntime {

    private final SbeGenerationEngine generationEngine = new SbeGenerationEngine();
    private final Path targetRoot;
    private final Path schemasDirectory;
    private final Path officialSourcesDirectory;
    private final Path adapterSourcesDirectory;
    private final Path runtimeClassesDirectory;

    public DebugRoundTripRuntime(final Path targetRoot) {
        this.targetRoot = targetRoot;
        this.schemasDirectory = targetRoot.resolve("schemas");
        this.officialSourcesDirectory = targetRoot.resolve("official-sources");
        this.adapterSourcesDirectory = targetRoot.resolve("adapter-sources");
        this.runtimeClassesDirectory = targetRoot.resolve("runtime-classes");
    }

    public void recreateOutputDirectories() throws IOException {
        recreateDirectory(schemasDirectory);
        recreateDirectory(officialSourcesDirectory);
        recreateDirectory(adapterSourcesDirectory);
        recreateDirectory(runtimeClassesDirectory);
    }

    public <T> RoundTripResult verify(final DebugRoundTripCase<T> roundTripCase) throws Exception {
        final Class<T> rootType = roundTripCase.type();
        final String token = SbeNaming.packageToken(rootType);
        final String generatedPackage = "ru.pathcreator.pyc.rpc.serialization.generated.supported." + token;
        final GenerationResult generationResult = generationEngine.generate(List.of(rootType), generatedPackage);

        final Path schemaFile = writeSchema(rootType, generationResult.resources());
        final Path officialSourceDirectory = officialSourcesDirectory.resolve(token);
        Files.createDirectories(officialSourceDirectory);
        SbeToolRunner.generateJavaFromSchema(
                schemaFile,
                officialSourceDirectory,
                SbeNaming.officialPackageName(generatedPackage, rootType)
        );

        final Path adapterSourceDirectory = adapterSourcesDirectory.resolve(token);
        Files.createDirectories(adapterSourceDirectory);
        writeJavaSources(generationResult.javaSources(), adapterSourceDirectory);
        final Path runtimeOutputDirectory = runtimeClassesDirectory.resolve(token);
        Files.createDirectories(runtimeOutputDirectory);
        compileGeneratedSources(adapterSourceDirectory, officialSourceDirectory, runtimeOutputDirectory);

        try (LoadedCodec<T> loadedCodec = loadCodec(rootType, generatedPackage, runtimeOutputDirectory)) {
            final SerializationCodec<T> codec = loadedCodec.codec();
            final T sample = roundTripCase.sample();
            final int measured = codec.measure(sample);
            final byte[] bytes = new byte[measured];
            final int written = codec.encode(sample, new UnsafeBuffer(bytes), 0);
            if (written > measured) {
                throw new IllegalStateException(rootType.getName() + " -> measure=" + measured + ", written=" + written);
            }
            final byte[] payload = written == bytes.length ? bytes : Arrays.copyOf(bytes, written);
            final T decoded = codec.decode(payload, 0, payload.length);
            DebugValueAssertions.assertDeepEquals(sample, decoded, rootType.getSimpleName());
            return new RoundTripResult(rootType, payload.length, generatedPackage, measured, written);
        }
    }

    public Path schemasDirectory() {
        return schemasDirectory;
    }

    public Path officialSourcesDirectory() {
        return officialSourcesDirectory;
    }

    private Path writeSchema(
            final Class<?> rootType,
            final List<GeneratedResource> resources
    ) throws IOException {
        final String relativePath = "META-INF/rpc-serialization/sbe/" + rootType.getName().replace('.', '/') + ".xml";
        final GeneratedResource resource = resources.stream()
                .filter(candidate -> Objects.equals(candidate.relativePath(), relativePath))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Schema resource not generated for " + rootType.getName()));
        final Path schemaFile = schemasDirectory.resolve(relativePath);
        Files.createDirectories(schemaFile.getParent());
        Files.writeString(schemaFile, resource.content(), StandardCharsets.UTF_8);
        return schemaFile;
    }

    private void writeJavaSources(
            final List<GeneratedJavaSource> sources,
            final Path outputDirectory
    ) throws IOException {
        for (final GeneratedJavaSource source : sources) {
            final Path file = outputDirectory.resolve(source.fullyQualifiedClassName().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.sourceCode(), StandardCharsets.UTF_8);
        }
    }

    private void compileGeneratedSources(
            final Path adapterSourceDirectory,
            final Path officialSourceDirectory,
            final Path outputDirectory
    ) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available. Run with a JDK.");
        }
        final List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(adapterSourceDirectory)) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }
        try (Stream<Path> stream = Files.walk(officialSourceDirectory)) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }
        final List<String> options = List.of(
                "--release", "25",
                "-d", outputDirectory.toAbsolutePath().toString(),
                "-classpath", classpath()
        );
        try (final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            final boolean ok = compiler.getTask(
                    null,
                    fileManager,
                    diagnostic -> System.err.println(diagnostic),
                    options,
                    null,
                    fileManager.getJavaFileObjectsFromFiles(javaFiles.stream().map(Path::toFile).toList())
            ).call();
            if (!ok) {
                throw new IllegalStateException("Compilation of generated debug sources failed for " + outputDirectory);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> LoadedCodec<T> loadCodec(
            final Class<T> rootType,
            final String generatedPackage,
            final Path runtimeOutputDirectory
    ) throws Exception {
        final URLClassLoader classLoader = new URLClassLoader(
                new URL[]{runtimeOutputDirectory.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()
        );
        try {
            final Class<?> factoryType = Class.forName(
                    generatedPackage + ".SerializationCodecFactoryImpl",
                    true,
                    classLoader
            );
            final SerializationCodecFactory factory = (SerializationCodecFactory) factoryType.getDeclaredConstructor().newInstance();
            for (final SerializationCodec<?> codec : factory.codecs()) {
                if (codec.javaType().equals(rootType)) {
                    return new LoadedCodec<>(classLoader, (SerializationCodec<T>) codec);
                }
            }
            throw new IllegalStateException("Codec not generated for " + rootType.getName());
        } catch (final Exception e) {
            classLoader.close();
            throw e;
        }
    }

    private String classpath() {
        final List<String> parts = new ArrayList<>();
        parts.add(Path.of("C:", "project", "rpc", "serialization", "target", "classes").toString());
        parts.add(Path.of("C:", "project", "rpc", "codec", "target", "classes").toString());
        parts.add(Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "agrona", "agrona", "2.4.1", "agrona-2.4.1.jar").toString());
        return String.join(System.getProperty("path.separator"), parts);
    }

    private static void recreateDirectory(final Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (final IOException e) {
                                throw new IllegalStateException("Failed to clean " + directory, e);
                            }
                        });
            }
        }
        Files.createDirectories(directory);
    }

    public record RoundTripResult(
            Class<?> type,
            int payloadSize,
            String generatedPackage,
            int measuredSize,
            int writtenSize
    ) {
        @Override
        public String toString() {
            return type.getSimpleName()
                   + " OK, payload=" + payloadSize + "B, package=" + generatedPackage.toLowerCase(Locale.ROOT);
        }

        public boolean hasMeasureMismatch() {
            return measuredSize != writtenSize;
        }
    }

    private record LoadedCodec<T>(
            URLClassLoader classLoader,
            SerializationCodec<T> codec
    ) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            classLoader.close();
        }
    }
}