package ru.pathcreator.pyc.rpc.spring;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

final class RpcSpringClassPathScanner extends ClassPathScanningCandidateComponentProvider {

    RpcSpringClassPathScanner(
            final boolean useDefaultFilters
    ) {
        super(useDefaultFilters);
    }

    @Override
    protected boolean isCandidateComponent(
            final AnnotatedBeanDefinition beanDefinition
    ) {
        return beanDefinition.getMetadata().isIndependent();
    }
}