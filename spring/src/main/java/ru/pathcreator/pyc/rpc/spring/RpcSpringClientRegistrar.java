package ru.pathcreator.pyc.rpc.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;
import ru.pathcreator.pyc.rpc.spring.annotation.RpcEndpoint;
import ru.pathcreator.pyc.rpc.spring.config.RpcSpringProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class RpcSpringClientRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ResourceLoaderAware, BeanClassLoaderAware, Ordered {

    private Environment environment;
    private ResourceLoader resourceLoader;
    private ClassLoader beanClassLoader;

    @Override
    public void setEnvironment(
            final Environment environment
    ) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(
            final ResourceLoader resourceLoader
    ) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setBeanClassLoader(
            final ClassLoader classLoader
    ) {
        this.beanClassLoader = classLoader;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(
            final BeanDefinitionRegistry registry
    ) throws BeansException {
        final RpcSpringProperties properties = bindProperties();
        if (!properties.isEnabled() || properties.getChannels().isEmpty()) {
            return;
        }
        final List<String> scanPackages = scanPackages(registry, properties);
        final Set<String> localServiceInterfaces = scanLocalEndpointInterfaces(scanPackages);
        final Set<Class<?>> serviceTypes = scanServiceInterfaces(scanPackages);
        for (final Class<?> serviceType : serviceTypes) {
            final RpcService service = serviceType.getAnnotation(RpcService.class);
            final String channelName = service.channel().isBlank() ? "default" : service.channel();
            final RpcSpringProperties.Channel channel = properties.getChannels().get(channelName);
            if (channel == null || !channel.isRegisterClientBeans()) {
                continue;
            }
            if (localServiceInterfaces.contains(serviceType.getName())) {
                continue;
            }
            registerClientBean(registry, serviceType);
        }
    }

    @Override
    public void postProcessBeanFactory(
            final ConfigurableListableBeanFactory beanFactory
    ) {
    }

    private void registerClientBean(
            final BeanDefinitionRegistry registry,
            final Class<?> serviceType
    ) {
        final String beanName = "rpcClient$" + serviceType.getName();
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }
        final RootBeanDefinition definition = new RootBeanDefinition(RpcSpringClientFactoryBean.class);
        definition.setTargetType(serviceType);
        definition.setLazyInit(true);
        definition.setInstanceSupplier(() -> new RpcSpringClientFactoryBean<>(
                ((ConfigurableListableBeanFactory) registry).getBean(RpcSpringClientFactory.class),
                serviceType
        ));
        registry.registerBeanDefinition(beanName, definition);
    }

    private RpcSpringProperties bindProperties() {
        return Binder.get(this.environment)
                .bind("rpc.spring", Bindable.of(RpcSpringProperties.class))
                .orElseGet(RpcSpringProperties::new);
    }

    private List<String> scanPackages(
            final BeanDefinitionRegistry registry,
            final RpcSpringProperties properties
    ) {
        if (!properties.getScanPackages().isEmpty()) {
            return List.copyOf(properties.getScanPackages());
        }
        if (registry instanceof ConfigurableListableBeanFactory beanFactory && AutoConfigurationPackages.has(beanFactory)) {
            return AutoConfigurationPackages.get(beanFactory);
        }
        throw new IllegalStateException(
                "Unable to determine Spring scan packages for RPC. Configure rpc.spring.scan-packages explicitly."
        );
    }

    private Set<Class<?>> scanServiceInterfaces(
            final List<String> packages
    ) {
        final Set<Class<?>> serviceTypes = new LinkedHashSet<>();
        final RpcSpringClassPathScanner scanner = new RpcSpringClassPathScanner(false);
        scanner.setResourceLoader(this.resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RpcService.class));
        for (final String scanPackage : packages) {
            for (final BeanDefinition candidate : scanner.findCandidateComponents(scanPackage)) {
                serviceTypes.add(loadClass(candidate.getBeanClassName()));
            }
        }
        return serviceTypes;
    }

    private Set<String> scanLocalEndpointInterfaces(
            final List<String> packages
    ) {
        final Set<String> interfaces = new LinkedHashSet<>();
        final RpcSpringClassPathScanner scanner = new RpcSpringClassPathScanner(false);
        scanner.setResourceLoader(this.resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RpcEndpoint.class));
        for (final String scanPackage : packages) {
            for (final BeanDefinition candidate : scanner.findCandidateComponents(scanPackage)) {
                final Class<?> endpointType = loadClass(candidate.getBeanClassName());
                final RpcEndpoint endpoint = endpointType.getAnnotation(RpcEndpoint.class);
                if (endpoint != null && endpoint.service() != Void.class) {
                    interfaces.add(endpoint.service().getName());
                    continue;
                }
                for (final Class<?> iface : endpointType.getInterfaces()) {
                    if (iface.isAnnotationPresent(RpcService.class)) {
                        interfaces.add(iface.getName());
                    }
                }
            }
        }
        return interfaces;
    }

    private Class<?> loadClass(
            final String className
    ) {
        try {
            return ClassUtils.forName(className, this.beanClassLoader);
        } catch (final ClassNotFoundException error) {
            throw new IllegalStateException("Failed to load RPC Spring class: " + className, error);
        }
    }
}