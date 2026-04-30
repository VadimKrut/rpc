package ru.pathcreator.pyc.rpc.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.spring.config.RpcSpringProperties;

@AutoConfiguration
@ConditionalOnClass(RpcBootstrapEnvironment.class)
@ConditionalOnProperty(prefix = "rpc.spring", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RpcSpringProperties.class)
public class RpcSpringAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RpcRuntime rpcRuntime() {
        return RpcRuntime.launchEmbedded();
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcBootstrapEnvironment rpcBootstrapEnvironment(
            final RpcRuntime runtime,
            final RpcSpringProperties properties
    ) {
        return RpcSpringSupport.createEnvironment(runtime, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcSpringClientFactory rpcSpringClientFactory(
            final RpcBootstrapEnvironment environment
    ) {
        return new RpcSpringClientFactory(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcSpringServiceExporter rpcSpringServiceExporter(
            final ApplicationContext applicationContext,
            final RpcBootstrapEnvironment environment
    ) {
        return new RpcSpringServiceExporter(applicationContext, environment);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static RpcSpringClientRegistrar rpcSpringClientRegistrar() {
        return new RpcSpringClientRegistrar();
    }
}