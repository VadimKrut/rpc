package ru.pathcreator.pyc.rpc.bootstrap;

import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.bootstrap.processor.RpcBootstrapAnnotationProcessor;

import javax.tools.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RpcBootstrapAnnotationProcessorTest {

    @Test
    void shouldGenerateDtoMetadataForAnnotatedService() throws IOException {
        final CompilationResult compilation = compile(
                "test.ValidService",
                """
                        package test;
                        
                        import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
                        import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;
                        
                        final class RequestDto {
                        }
                        
                        final class ResponseDto {
                        }
                        
                        @RpcService("test.valid")
                        interface ValidService {
                        
                            @RpcMethod(requestMessageTypeId = 101, responseMessageTypeId = 201)
                            ResponseDto echo(RequestDto request);
                        }
                        """
        );
        assertTrue(compilation.success(), compilation.diagnostics());
        final Path metadataFile = compilation.outputDirectory()
                .resolve("META-INF/rpc-bootstrap/dto-types/test_ValidService.list");
        assertTrue(Files.exists(metadataFile), "bootstrap metadata file must be generated");
        final String metadata = Files.readString(metadataFile, StandardCharsets.UTF_8);
        assertTrue(metadata.contains("test.RequestDto"));
        assertTrue(metadata.contains("test.ResponseDto"));
    }

    @Test
    void shouldFailCompilationForInvalidAnnotatedService() throws IOException {
        final CompilationResult compilation = compile(
                "test.InvalidService",
                """
                        package test;
                        
                        import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
                        import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;
                        
                        @RpcService
                        interface InvalidService {
                        
                            @RpcMethod(requestMessageTypeId = 1, responseMessageTypeId = 1)
                            void bad(String left, String right);
                        
                            @RpcMethod(requestMessageTypeId = 2, responseMessageTypeId = 1)
                            String alsoBad(String request);
                        }
                        """
        );
        assertFalse(compilation.success(), "invalid bootstrap service must fail compilation");
        assertTrue(compilation.diagnostics().contains("exactly one parameter"), compilation.diagnostics());
        assertTrue(compilation.diagnostics().contains("must not return void"), compilation.diagnostics());
        assertTrue(compilation.diagnostics().contains("Duplicate responseMessageTypeId"), compilation.diagnostics());
    }

    private static CompilationResult compile(
            final String className,
            final String sourceCode
    ) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final Path outputDirectory = Files.createTempDirectory("rpc-bootstrap-processor");
        final List<String> options = List.of(
                "--release", "25",
                "-classpath", System.getProperty("java.class.path"),
                "-d", outputDirectory.toString()
        );
        try (final StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            final JavaFileObject source = new StringJavaFileObject(className, sourceCode);
            final JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(source)
            );
            task.setProcessors(List.of(new RpcBootstrapAnnotationProcessor()));
            final boolean success = Boolean.TRUE.equals(task.call());
            return new CompilationResult(
                    success,
                    outputDirectory,
                    diagnostics.getDiagnostics().stream().map(Object::toString).toList().toString()
            );
        }
    }

    private record CompilationResult(
            boolean success,
            Path outputDirectory,
            String diagnostics
    ) {
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {

        private final String sourceCode;

        private StringJavaFileObject(
                final String className,
                final String sourceCode
        ) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(
                final boolean ignoreEncodingErrors
        ) {
            return this.sourceCode;
        }
    }
}