package ru.pathcreator.pyc.rpc.bootstrap;

import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedMethod;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedService;
import ru.pathcreator.pyc.rpc.client.RpcClient;
import ru.pathcreator.pyc.rpc.client.listener.RpcClientListener;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInterceptor;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientRequestValidator;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientResponseValidator;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.observability.client.RpcClientMetrics;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMetrics;
import ru.pathcreator.pyc.rpc.server.RpcServer;
import ru.pathcreator.pyc.rpc.server.error.RpcServerExceptionMapper;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInterceptor;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerRequestValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RpcBootstrapEnvironment {

    private final Map<String, ProfileConfig> profiles;
    private final Map<String, RpcClient> clients = new LinkedHashMap<>();
    private final Map<String, RpcServer> servers = new LinkedHashMap<>();
    private final Map<String, java.util.Set<String>> registeredServiceNames = new LinkedHashMap<>();
    private final Map<String, Map<Integer, String>> requestMethodOwners = new LinkedHashMap<>();
    private final Map<String, Map<Integer, String>> responseMethodOwners = new LinkedHashMap<>();
    private final Map<String, List<RegisteredServiceSnapshot>> registeredServices = new LinkedHashMap<>();

    RpcBootstrapEnvironment(
            final Map<String, ProfileConfig> profiles
    ) {
        this.profiles = Map.copyOf(profiles);
    }

    public synchronized RpcClient client(
            final String profileName
    ) {
        return this.clients.computeIfAbsent(profileName, this::buildClient);
    }

    public synchronized RpcServer server(
            final String profileName
    ) {
        return this.servers.computeIfAbsent(profileName, this::buildServer);
    }

    public <T> T client(
            final Class<T> serviceType
    ) {
        final RpcAnnotatedService<T> service = RpcBootstrap.introspect(serviceType);
        return RpcBootstrapClientBuilder.bind(this.client(service.channelName()), serviceType);
    }

    public synchronized RpcBootstrapEnvironment service(
            final Object implementation
    ) {
        if (implementation == null) {
            throw new IllegalArgumentException("implementation must not be null");
        }
        final Class<?> serviceType = resolveAnnotatedServiceType(implementation.getClass());
        return this.serviceResolved(serviceType, implementation);
    }

    public synchronized RpcBootstrapEnvironment services(
            final Object... implementations
    ) {
        if (implementations == null) {
            throw new IllegalArgumentException("implementations must not be null");
        }
        for (final Object implementation : implementations) {
            this.service(implementation);
        }
        return this;
    }

    public synchronized <T> RpcBootstrapEnvironment service(
            final Class<T> serviceType,
            final T implementation
    ) {
        if (implementation == null) {
            throw new IllegalArgumentException("implementation must not be null");
        }
        final RpcAnnotatedService<T> service = RpcBootstrap.introspect(serviceType);
        this.ensureUniqueMethodIds(service);
        this.registerServiceSnapshot(service);
        RpcBootstrapServerBuilder.register(this.server(service.channelName()), serviceType, implementation);
        return this;
    }

    public synchronized RpcBootstrapEnvironment validate(
            final Class<?>... serviceTypes
    ) {
        if (serviceTypes == null) {
            throw new IllegalArgumentException("serviceTypes must not be null");
        }
        final Map<String, java.util.Set<String>> serviceNames = copyServiceNames();
        final Map<String, Map<Integer, String>> requestOwners = copyOwners(this.requestMethodOwners);
        final Map<String, Map<Integer, String>> responseOwners = copyOwners(this.responseMethodOwners);
        for (final Class<?> serviceType : serviceTypes) {
            this.validateService(serviceType, serviceNames, requestOwners, responseOwners);
        }
        return this;
    }

    public synchronized boolean hasProfile(
            final String profileName
    ) {
        return this.profiles.containsKey(profileName);
    }

    public synchronized List<String> profileNames() {
        return List.copyOf(this.profiles.keySet());
    }

    public synchronized RpcChannel channel(
            final String profileName
    ) {
        return this.requireProfile(profileName).channel();
    }

    public synchronized RpcClient clientIfPresent(
            final String profileName
    ) {
        return this.clients.get(profileName);
    }

    public synchronized RpcServer serverIfPresent(
            final String profileName
    ) {
        return this.servers.get(profileName);
    }

    public synchronized List<RegisteredServiceSnapshot> registeredServices() {
        final List<RegisteredServiceSnapshot> snapshots = new ArrayList<>();
        for (final List<RegisteredServiceSnapshot> services : this.registeredServices.values()) {
            snapshots.addAll(services);
        }
        return List.copyOf(snapshots);
    }

    private RpcClient buildClient(
            final String profileName
    ) {
        final ProfileConfig profile = this.requireProfile(profileName);
        final RpcBootstrapClientBuilder builder = RpcBootstrap.client(profile.channel())
                .defaultTimeoutNs(profile.clientDefaultTimeoutNs())
                .requestValidator(profile.clientRequestValidator())
                .responseValidator(profile.clientResponseValidator())
                .listener(profile.clientListener())
                .payloadEncryption(profile.clientPayloadEncryption());
        if (profile.clientMetrics() != null) {
            builder.metrics(profile.clientMetrics());
        }
        for (final RpcClientInterceptor interceptor : profile.clientInterceptors()) {
            builder.interceptor(interceptor);
        }
        return builder.buildClient();
    }

    private RpcServer buildServer(
            final String profileName
    ) {
        final ProfileConfig profile = this.requireProfile(profileName);
        final RpcBootstrapServerBuilder builder = RpcBootstrap.server(profile.channel())
                .exceptionMapper(profile.serverExceptionMapper())
                .requestValidator(profile.serverRequestValidator())
                .listener(profile.serverListener())
                .payloadEncryption(profile.serverPayloadEncryption());
        if (profile.serverMetrics() != null) {
            builder.metrics(profile.serverMetrics());
        }
        for (final RpcServerInterceptor interceptor : profile.serverInterceptors()) {
            builder.interceptor(interceptor);
        }
        return builder.build();
    }

    private ProfileConfig requireProfile(
            final String profileName
    ) {
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("profileName must not be blank");
        }
        final ProfileConfig profile = this.profiles.get(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("bootstrap channel profile is not configured: " + profileName);
        }
        return profile;
    }

    private void ensureUniqueMethodIds(
            final RpcAnnotatedService<?> service
    ) {
        this.ensureUniqueMethodIds(
                service,
                this.registeredServiceNames,
                this.requestMethodOwners,
                this.responseMethodOwners
        );
    }

    private void ensureUniqueMethodIds(
            final RpcAnnotatedService<?> service,
            final Map<String, java.util.Set<String>> serviceNames,
            final Map<String, Map<Integer, String>> requestOwnersByProfile,
            final Map<String, Map<Integer, String>> responseOwnersByProfile
    ) {
        final String profileName = service.channelName();
        this.requireProfile(profileName);
        final java.util.Set<String> registeredServices = serviceNames.computeIfAbsent(profileName, ignored -> new java.util.LinkedHashSet<>());
        if (!registeredServices.add(service.contract().name())) {
            throw new IllegalStateException(
                    "service '" + service.contract().name() + "' is already registered in bootstrap channel profile '" + profileName + "'"
            );
        }
        final Map<Integer, String> requestOwners = requestOwnersByProfile.computeIfAbsent(profileName, ignored -> new LinkedHashMap<>());
        final Map<Integer, String> responseOwners = responseOwnersByProfile.computeIfAbsent(profileName, ignored -> new LinkedHashMap<>());
        for (final RpcAnnotatedMethod method : service.methods()) {
            final String requestOwner = requestOwners.putIfAbsent(method.contract().requestMessageTypeId(), method.contract().name());
            if (requestOwner != null && !requestOwner.equals(method.contract().name())) {
                throw new IllegalStateException(
                        "requestMessageTypeId " + method.contract().requestMessageTypeId()
                        + " is already registered in bootstrap channel profile '" + profileName
                        + "' by method '" + requestOwner + "'"
                );
            }
            final String responseOwner = responseOwners.putIfAbsent(method.contract().responseMessageTypeId(), method.contract().name());
            if (responseOwner != null && !responseOwner.equals(method.contract().name())) {
                throw new IllegalStateException(
                        "responseMessageTypeId " + method.contract().responseMessageTypeId()
                        + " is already registered in bootstrap channel profile '" + profileName
                        + "' by method '" + responseOwner + "'"
                );
            }
        }
    }

    private void validateService(
            final Class<?> serviceType,
            final Map<String, java.util.Set<String>> serviceNames,
            final Map<String, Map<Integer, String>> requestOwners,
            final Map<String, Map<Integer, String>> responseOwners
    ) {
        if (serviceType == null) {
            throw new IllegalArgumentException("serviceType must not be null");
        }
        this.ensureUniqueMethodIds(RpcBootstrap.introspect(serviceType), serviceNames, requestOwners, responseOwners);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> resolveAnnotatedServiceType(
            final Class<?> implementationType
    ) {
        final List<Class<?>> matches = new java.util.ArrayList<>();
        collectAnnotatedInterfaces(implementationType, matches);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "implementation does not expose an interface annotated with @RpcService: " + implementationType.getName()
            );
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "implementation exposes multiple @RpcService interfaces, specify the service type explicitly: "
                    + implementationType.getName()
            );
        }
        return (Class<T>) matches.getFirst();
    }

    private static void collectAnnotatedInterfaces(
            final Class<?> implementationType,
            final List<Class<?>> matches
    ) {
        if (implementationType == null || implementationType == Object.class) {
            return;
        }
        for (final Class<?> candidate : implementationType.getInterfaces()) {
            if (candidate.isAnnotationPresent(ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService.class) && !matches.contains(candidate)) {
                matches.add(candidate);
            }
            collectAnnotatedInterfaces(candidate, matches);
        }
        collectAnnotatedInterfaces(implementationType.getSuperclass(), matches);
    }

    private Map<String, java.util.Set<String>> copyServiceNames() {
        final Map<String, java.util.Set<String>> copy = new LinkedHashMap<>(this.registeredServiceNames.size());
        for (final Map.Entry<String, java.util.Set<String>> entry : this.registeredServiceNames.entrySet()) {
            copy.put(entry.getKey(), new java.util.LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private static Map<String, Map<Integer, String>> copyOwners(
            final Map<String, Map<Integer, String>> source
    ) {
        final Map<String, Map<Integer, String>> copy = new LinkedHashMap<>(source.size());
        for (final Map.Entry<String, Map<Integer, String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private <T> RpcBootstrapEnvironment serviceResolved(
            final Class<?> serviceType,
            final Object implementation
    ) {
        return this.service((Class<T>) serviceType, (T) implementation);
    }

    private void registerServiceSnapshot(
            final RpcAnnotatedService<?> service
    ) {
        final List<RegisteredMethodSnapshot> methods = new ArrayList<>(service.methods().size());
        for (final RpcAnnotatedMethod method : service.methods()) {
            methods.add(
                    new RegisteredMethodSnapshot(
                            method.contract().name(),
                            method.contract().requestMessageTypeId(),
                            method.contract().responseMessageTypeId()
                    )
            );
        }
        this.registeredServices
                .computeIfAbsent(service.channelName(), ignored -> new ArrayList<>())
                .add(new RegisteredServiceSnapshot(service.channelName(), service.contract().name(), List.copyOf(methods)));
    }

    static record ProfileConfig(
            String name,
            RpcChannel channel,
            long clientDefaultTimeoutNs,
            RpcClientRequestValidator clientRequestValidator,
            RpcClientResponseValidator clientResponseValidator,
            List<RpcClientInterceptor> clientInterceptors,
            RpcClientListener clientListener,
            RpcClientMetrics clientMetrics,
            RpcPayloadEncryption clientPayloadEncryption,
            RpcServerExceptionMapper serverExceptionMapper,
            RpcServerRequestValidator serverRequestValidator,
            List<RpcServerInterceptor> serverInterceptors,
            RpcServerListener serverListener,
            RpcServerMetrics serverMetrics,
            RpcPayloadEncryption serverPayloadEncryption
    ) {
    }

    public record RegisteredServiceSnapshot(
            String profileName,
            String serviceName,
            List<RegisteredMethodSnapshot> methods
    ) {
    }

    public record RegisteredMethodSnapshot(
            String methodName,
            int requestMessageTypeId,
            int responseMessageTypeId
    ) {
    }
}