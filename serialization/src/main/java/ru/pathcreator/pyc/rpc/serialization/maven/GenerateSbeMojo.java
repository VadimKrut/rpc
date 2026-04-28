package ru.pathcreator.pyc.rpc.serialization.maven;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GeneratedJavaSource;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GeneratedResource;
import ru.pathcreator.pyc.rpc.serialization.codegen.artifact.GenerationResult;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen.SbeGenerationEngine;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen.SbeNaming;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.tool.SbeToolRunner;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(
        name = "generate-sbe",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class GenerateSbeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/rpc-serialization/sbe", required = true)
    private File generatedSourcesDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-resources/rpc-serialization/sbe", required = true)
    private File generatedResourcesDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/rpc-serialization/sbe-official", required = true)
    private File generatedOfficialSourcesDirectory;

    @Parameter(property = "rpc.generatedPackage", defaultValue = "${project.groupId}.rpc.serialization.generated")
    private String generatedPackage;

    @Parameter(defaultValue = "${maven.compiler.release}")
    private String compilerRelease;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!classesDirectory.exists()) {
            getLog().info("No compiled classes found, skipping rpc-serialization generation.");
            return;
        }
        try {
            final List<Class<?>> annotatedTypes = discoverAnnotatedTypes();
            if (annotatedTypes.isEmpty()) {
                getLog().info("No @SbeSerializable types found.");
                return;
            }
            final GenerationResult bundle = new SbeGenerationEngine().generate(annotatedTypes, generatedPackage);
            writeJavaSources(bundle.javaSources());
            writeResources(bundle.resources());
            generateOfficialSbeSources(annotatedTypes);
            compileGeneratedSources();
        } catch (final IllegalStateException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (final Exception e) {
            throw new MojoExecutionException("Failed to generate RPC codecs", e);
        }
    }

    private List<Class<?>> discoverAnnotatedTypes() throws IOException, DependencyResolutionRequiredException, ClassNotFoundException {
        List<Path> classFiles;
        try (final Stream<Path> stream = Files.walk(classesDirectory.toPath())) {
            classFiles = stream
                    .filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().contains("$Generated"))
                    .collect(Collectors.toList());
        }
        final List<URL> urls = new ArrayList<>();
        urls.add(classesDirectory.toURI().toURL());
        for (final String element : project.getCompileClasspathElements()) {
            urls.add(new File(element).toURI().toURL());
        }
        final List<Class<?>> annotated = new ArrayList<>();
        try (final URLClassLoader classLoader = new URLClassLoader(urls.toArray(URL[]::new), Thread.currentThread().getContextClassLoader())) {
            for (final Path classFile : classFiles) {
                final String className = toClassName(classFile);
                final Class<?> type = classLoader.loadClass(className);
                if (type.isAnnotationPresent(SbeSerializable.class)) {
                    annotated.add(type);
                }
            }
        }
        return annotated;
    }

    private String toClassName(final Path classFile) {
        final String relative = classesDirectory.toPath().relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length()).replace(File.separatorChar, '.');
    }

    private void writeJavaSources(final List<GeneratedJavaSource> sources) throws IOException {
        for (final GeneratedJavaSource source : sources) {
            final Path file = generatedSourcesDirectory.toPath().resolve(source.fullyQualifiedClassName().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.sourceCode(), StandardCharsets.UTF_8);
        }
    }

    private void writeResources(final List<GeneratedResource> resources) throws IOException {
        for (final GeneratedResource resource : resources) {
            final Path generated = generatedResourcesDirectory.toPath().resolve(resource.relativePath());
            final Path classesTarget = classesDirectory.toPath().resolve(resource.relativePath());
            Files.createDirectories(generated.getParent());
            Files.createDirectories(classesTarget.getParent());
            Files.writeString(generated, resource.content(), StandardCharsets.UTF_8);
            Files.writeString(classesTarget, resource.content(), StandardCharsets.UTF_8);
        }
    }

    private void generateOfficialSbeSources(final List<Class<?>> annotatedTypes) throws IOException {
        for (final Class<?> annotatedType : annotatedTypes) {
            final Path schemaFile = generatedResourcesDirectory.toPath()
                    .resolve("META-INF/rpc-serialization/sbe/" + annotatedType.getName().replace('.', '/') + ".xml");
            final Path outputDirectory = generatedOfficialSourcesDirectory.toPath().resolve(annotatedType.getName().replace('.', '_'));
            Files.createDirectories(outputDirectory);
            SbeToolRunner.generateJavaFromSchema(
                    schemaFile,
                    outputDirectory,
                    SbeNaming.officialPackageName(generatedPackage, annotatedType)
            );
        }
    }

    private void compileGeneratedSources() throws IOException, DependencyResolutionRequiredException, MojoFailureException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new MojoFailureException("No system Java compiler available. Run Maven with a JDK.");
        }
        final List<File> javaFiles = new ArrayList<>();
        for (final Path root : List.of(generatedSourcesDirectory.toPath(), generatedOfficialSourcesDirectory.toPath())) {
            if (!Files.exists(root)) {
                continue;
            }
            try (final Stream<Path> stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .map(Path::toFile)
                        .forEach(javaFiles::add);
            }
        }
        if (javaFiles.isEmpty()) {
            return;
        }
        final List<String> options = new ArrayList<>();
        options.add("--release");
        options.add(compilerRelease == null || compilerRelease.isBlank() ? "25" : compilerRelease);
        options.add("-d");
        options.add(classesDirectory.getAbsolutePath());
        options.add("-classpath");
        options.add(classpath());
        try (final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            final boolean ok = compiler.getTask(
                    null,
                    fileManager,
                    diagnostic -> getLog().error(diagnostic.toString()),
                    options,
                    null,
                    fileManager.getJavaFileObjectsFromFiles(javaFiles)
            ).call();
            if (!ok) {
                throw new MojoFailureException("Compilation of generated RPC codecs failed.");
            }
        }
        project.addCompileSourceRoot(generatedSourcesDirectory.getAbsolutePath());
        project.addCompileSourceRoot(generatedOfficialSourcesDirectory.getAbsolutePath());
    }

    private String classpath() throws DependencyResolutionRequiredException {
        final List<String> parts = new ArrayList<>();
        parts.add(classesDirectory.getAbsolutePath());
        parts.addAll(project.getCompileClasspathElements());
        return String.join(File.pathSeparator, parts);
    }
}