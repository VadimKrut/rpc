package ru.pathcreator.pyc.rpc.bootstrap.processor;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService")
public final class RpcBootstrapAnnotationProcessor extends AbstractProcessor {

    private static final String METADATA_DIRECTORY = "META-INF/rpc-bootstrap/dto-types";

    private Messager messager;
    private Filer filer;

    @Override
    public synchronized void init(
            final ProcessingEnvironment processingEnvironment
    ) {
        super.init(processingEnvironment);
        this.messager = processingEnvironment.getMessager();
        this.filer = processingEnvironment.getFiler();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(
            final Set<? extends TypeElement> annotations,
            final RoundEnvironment roundEnvironment
    ) {
        for (final Element element : roundEnvironment.getElementsAnnotatedWith(RpcService.class)) {
            if (!(element instanceof TypeElement serviceType)) {
                continue;
            }
            final ValidationResult result = validate(serviceType);
            if (!result.valid()) {
                continue;
            }
            try {
                writeMetadata(serviceType, result.dtoTypes());
            } catch (final IOException error) {
                this.error(serviceType, "Failed to write RPC bootstrap metadata: %s", error.getMessage());
            }
        }
        return false;
    }

    private ValidationResult validate(
            final TypeElement serviceType
    ) {
        if (serviceType.getKind() != ElementKind.INTERFACE) {
            this.error(serviceType, "@RpcService may only be used on interfaces");
            return ValidationResult.invalid();
        }
        final String serviceName = serviceType.getAnnotation(RpcService.class).value().isBlank()
                ? serviceType.getQualifiedName().toString()
                : serviceType.getAnnotation(RpcService.class).value();
        final String channelName = serviceType.getAnnotation(RpcService.class).channel().isBlank()
                ? "default"
                : serviceType.getAnnotation(RpcService.class).channel();
        if (channelName.isBlank()) {
            this.error(serviceType, "channel must not be blank");
            return ValidationResult.invalid();
        }
        final List<ExecutableElement> methods = ElementFilter.methodsIn(
                this.processingEnv.getElementUtils().getAllMembers(serviceType)
        );
        final Set<String> dtoTypes = new LinkedHashSet<>();
        final Set<String> methodNames = new LinkedHashSet<>();
        final Set<Integer> requestIds = new LinkedHashSet<>();
        final Set<Integer> responseIds = new LinkedHashSet<>();
        boolean valid = true;
        int rpcMethodCount = 0;
        for (final ExecutableElement method : methods) {
            if (method.getEnclosingElement().toString().equals(Object.class.getName())) {
                continue;
            }
            if (method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            rpcMethodCount++;
            if (method.getModifiers().contains(Modifier.DEFAULT)) {
                this.error(method, "Default RPC methods are not supported");
                valid = false;
                continue;
            }
            final RpcMethod rpcMethod = method.getAnnotation(RpcMethod.class);
            if (rpcMethod == null) {
                this.error(method, "Missing @RpcMethod");
                valid = false;
                continue;
            }
            if (method.getParameters().size() != 1) {
                this.error(method, "RPC bootstrap method must have exactly one parameter");
                valid = false;
            }
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                this.error(method, "RPC bootstrap method must not return void");
                valid = false;
            }
            if (rpcMethod.requestMessageTypeId() <= 0) {
                this.error(method, "requestMessageTypeId must be > 0");
                valid = false;
            }
            if (rpcMethod.responseMessageTypeId() <= 0) {
                this.error(method, "responseMessageTypeId must be > 0");
                valid = false;
            }
            final String contractName = rpcMethod.name().isBlank()
                    ? serviceName + "." + method.getSimpleName()
                    : rpcMethod.name();
            if (!methodNames.add(contractName)) {
                this.error(method, "Duplicate RPC method name in service: %s", contractName);
                valid = false;
            }
            if (!requestIds.add(rpcMethod.requestMessageTypeId())) {
                this.error(method, "Duplicate requestMessageTypeId in service: %s", rpcMethod.requestMessageTypeId());
                valid = false;
            }
            if (!responseIds.add(rpcMethod.responseMessageTypeId())) {
                this.error(method, "Duplicate responseMessageTypeId in service: %s", rpcMethod.responseMessageTypeId());
                valid = false;
            }
            if (method.getParameters().size() == 1) {
                valid &= addDtoType(dtoTypes, method.getParameters().getFirst());
            }
            valid &= addDtoType(dtoTypes, method.getReturnType(), method);
        }
        if (rpcMethodCount == 0) {
            this.error(serviceType, "RPC bootstrap service must declare at least one method");
            valid = false;
        }
        return valid ? ValidationResult.valid(dtoTypes) : ValidationResult.invalid();
    }

    private boolean addDtoType(
            final Set<String> dtoTypes,
            final VariableElement parameter
    ) {
        return addDtoType(dtoTypes, parameter.asType(), parameter);
    }

    private boolean addDtoType(
            final Set<String> dtoTypes,
            final javax.lang.model.type.TypeMirror type,
            final Element source
    ) {
        if (type.getKind() == TypeKind.DECLARED) {
            dtoTypes.add(type.toString());
            return true;
        }
        if (type.getKind().isPrimitive()) {
            this.error(source, "Primitive RPC DTO types are not supported in bootstrap contracts: %s", type);
            return false;
        }
        this.error(source, "Unsupported RPC DTO type in bootstrap contract: %s", type);
        return false;
    }

    private void writeMetadata(
            final TypeElement serviceType,
            final Set<String> dtoTypes
    ) throws IOException {
        final String fileName = METADATA_DIRECTORY + "/" + this.processingEnv.getElementUtils().getBinaryName(serviceType)
                .toString()
                .replace('.', '_') + ".list";
        final FileObject file = this.filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                fileName,
                serviceType
        );
        try (final Writer writer = file.openWriter()) {
            writer.write("# generated by RpcBootstrapAnnotationProcessor\n");
            for (final String dtoType : dtoTypes) {
                writer.write(dtoType);
                writer.write('\n');
            }
        }
    }

    private void error(
            final Element element,
            final String message,
            final Object... arguments
    ) {
        this.messager.printMessage(
                Diagnostic.Kind.ERROR,
                arguments.length == 0 ? message : String.format(message, arguments),
                element
        );
    }

    private record ValidationResult(
            boolean valid,
            Set<String> dtoTypes
    ) {

        private static ValidationResult valid(
                final Set<String> dtoTypes
        ) {
            return new ValidationResult(true, Set.copyOf(dtoTypes));
        }

        private static ValidationResult invalid() {
            return new ValidationResult(false, Set.of());
        }
    }
}