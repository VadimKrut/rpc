package ru.pathcreator.pyc.rpc.spring.fixture;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ServerEchoEndpoint.class)
public class SpringServerTestApplication {
}
